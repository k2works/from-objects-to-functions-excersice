//! PostgreSQL を通す結合テスト（第 9 章）。
//!
//! **既定では走りません。** `db` フィーチャを付けたときだけです
//! （Unit 5 のゲート 1）。書いている間は docker 無しで `just check` が通ります。
//!
//! ```bash
//! docker compose up -d zettai-db
//! just test-db
//! ```
#![cfg(feature = "db")]

use std::sync::atomic::{AtomicUsize, Ordering};
use zettai_step5_domain::store::{handle_with_store, load, EventStore};
use zettai_step5_domain::{ListName, ToDoItem, ToDoListCommand, User, ZettaiError};
use zettai_step5_http::event_store::PostgresEventStore;

const CONN: &str = "host=localhost user=zettai password=zettai dbname=zettai";

/// テストごとに違うテーブルを使う。
///
/// なでしこ3 版 Unit 7 では記録先を 1 つに決め打ちし、
/// **並列に走るテストが同じ場所に書いて件数が混ざりました。**
fn unique_table(label: &str) -> String {
    static COUNTER: AtomicUsize = AtomicUsize::new(0);
    let n = COUNTER.fetch_add(1, Ordering::SeqCst);
    format!("zettai_events_{label}_{}_{n}", std::process::id())
}

fn store_for(label: &str) -> PostgresEventStore {
    PostgresEventStore::connect(CONN, &unique_table(label)).expect("DB が起動している")
}

fn uberto() -> User {
    User::new("uberto")
}
fn book() -> ListName {
    ListName::new("book")
}

#[test]
fn a_list_survives_a_new_connection() {
    let label = "survive";
    let table = {
        let store = store_for(label);
        handle_with_store(
            &store,
            &uberto(),
            &book(),
            ToDoListCommand::CreateList { list_name: book() },
        )
        .expect("作れる");
        handle_with_store(
            &store,
            &uberto(),
            &book(),
            ToDoListCommand::AddItem {
                item: ToDoItem::new("write chapter"),
            },
        )
        .expect("足せる");
        store.table_name().to_string()
    };

    // **つなぎ直す。** プロセスの中の状態に頼っていないことを確かめる。
    let reopened = PostgresEventStore::connect(CONN, &table).expect("つなぎ直せる");
    let list = load(&reopened, &uberto(), &book()).expect("読み戻せる");

    assert_eq!(list.items.len(), 1);
    assert_eq!(list.items[0], ToDoItem::new("write chapter"));
    reopened.drop_table().expect("片付けられる");
}

#[test]
fn another_list_is_not_mixed_in() {
    let store = store_for("mix");
    let shopping = ListName::new("shopping");

    handle_with_store(
        &store,
        &uberto(),
        &book(),
        ToDoListCommand::CreateList { list_name: book() },
    )
    .expect("作れる");
    handle_with_store(
        &store,
        &uberto(),
        &shopping,
        ToDoListCommand::CreateList {
            list_name: shopping.clone(),
        },
    )
    .expect("作れる");
    handle_with_store(
        &store,
        &uberto(),
        &book(),
        ToDoListCommand::AddItem {
            item: ToDoItem::new("write"),
        },
    )
    .expect("足せる");

    assert_eq!(load(&store, &uberto(), &book()).unwrap().items.len(), 1);
    assert_eq!(load(&store, &uberto(), &shopping).unwrap().items.len(), 0);
    store.drop_table().expect("片付けられる");
}

#[test]
fn a_rejected_command_writes_nothing() {
    let store = store_for("reject");

    let result = handle_with_store(
        &store,
        &uberto(),
        &book(),
        ToDoListCommand::AddItem {
            item: ToDoItem::new("write"),
        },
    );

    assert!(matches!(result, Err(ZettaiError::ListNotFound { .. })));
    assert!(store.events_of(&uberto(), &book()).unwrap().is_empty());
    store.drop_table().expect("片付けられる");
}

#[test]
fn an_unreachable_store_says_so() {
    let broken = PostgresEventStore::connect("host=localhost port=1 user=nobody", "x");
    assert!(matches!(broken, Err(ZettaiError::StoreUnavailable { .. })));
}

/// **3 経路目。** 同じシナリオを保管を通して走らせる。
///
/// 2 経路（ドメイン直接・HTTP 経由）では捕まらない欠陥がある、
/// というのが足す理由（[ADR-020]）。
#[test]
fn the_third_route_tells_the_same_story() {
    use zettai_step5_http::acceptance::{ThroughStore, ZettaiActions};

    let actions = ThroughStore::seeded(CONN, &unique_table("route"));

    assert_eq!(
        actions.items_of("uberto", "book"),
        Some(
            ["write chapter", "insert code", "publish book"]
                .map(String::from)
                .to_vec()
        ),
        "{}: uberto は book の項目を 3 件見られる",
        actions.route()
    );
    assert_eq!(
        actions.items_of("uberto", "shopping"),
        Some(vec![]),
        "{}: 空のリストも見られる",
        actions.route()
    );
    assert_eq!(
        actions.items_of("uberto", "nope"),
        None,
        "{}: 無いリストは見つからない",
        actions.route()
    );

    actions.add_item("uberto", "book", "review chapter");
    assert_eq!(actions.items_of("uberto", "book").unwrap().len(), 4);

    actions.drop_table();
}

// ---------------------------------------------------------------------------
// 第 10 章: 単位の中で処理する
// ---------------------------------------------------------------------------

// `TxEventStore` は import しない。**`&dyn TxEventStore` の主トレイトの
// メソッドは import なしで呼べる**（既知の制約 8。第 7 章で踏んだ）。
use zettai_step5_domain::store::{handle_in_transaction, load_in_transaction, TransactionalStore};

#[test]
fn a_command_in_a_unit_is_committed() {
    let store = store_for("unit_ok");

    handle_in_transaction(
        &store,
        &uberto(),
        &book(),
        ToDoListCommand::CreateList { list_name: book() },
    )
    .expect("作れる");

    let list = load_in_transaction(&store, &uberto(), &book()).expect("読める");
    assert_eq!(list.list_name, book());
    store.drop_table().expect("片付けられる");
}

/// **この Unit でいちばん確かめたいこと。**
///
/// 「読む → 判断する → 書く」の途中で失敗させ、**1 件も残らない**ことを見る。
/// 第 9 章までは `?` が 3 回並んでいるだけで、半分だけ残りえた。
#[test]
fn nothing_is_left_when_it_fails_halfway() {
    let store = store_for("unit_ng");

    // 1 件目は入れる。2 件目で、存在しない列に書こうとして落とす。
    let result = store.in_transaction(&|tx| {
        tx.append(
            &uberto(),
            &book(),
            &[zettai_step5_domain::ToDoListEvent::ListCreated { list_name: book() }],
        )?;
        // **わざと落とす。** この時点で 1 件目は既に INSERT されている
        Err(ZettaiError::StoreUnavailable {
            detail: "わざと落とした".to_string(),
        })
    });

    assert!(matches!(result, Err(ZettaiError::StoreUnavailable { .. })));
    assert!(
        store.events_of(&uberto(), &book()).unwrap().is_empty(),
        "1 件も残らない"
    );
    store.drop_table().expect("片付けられる");
}

#[test]
fn a_rejected_command_in_a_unit_writes_nothing() {
    let store = store_for("unit_reject");

    let result = handle_in_transaction(
        &store,
        &uberto(),
        &book(),
        ToDoListCommand::AddItem {
            item: ToDoItem::new("write"),
        },
    );

    assert!(matches!(result, Err(ZettaiError::ListNotFound { .. })));
    assert!(store.events_of(&uberto(), &book()).unwrap().is_empty());
    store.drop_table().expect("片付けられる");
}

/// **第 9 章までの形なら半分残る。** 上のテストが本物であることの裏づけ。
///
/// 単位を通さずに `EventStore::append` を 2 回呼び、2 回目の前に失敗したことにする。
/// **1 回目が残ってしまう。**
#[test]
fn the_old_shape_leaves_half() {
    let store = store_for("old_half");

    // 1 回目は通る
    EventStore::append(
        &store,
        &uberto(),
        &book(),
        &[zettai_step5_domain::ToDoListEvent::ListCreated { list_name: book() }],
    )
    .expect("入る");

    // ここで失敗したことにする（2 回目を呼ばない）
    let left = EventStore::events_of(&store, &uberto(), &book()).unwrap();

    assert_eq!(
        left.len(),
        1,
        "**半分だけ残っている。** これが第 10 章の出発点"
    );
    store.drop_table().expect("片付けられる");
}

// ---------------------------------------------------------------------------
// 第 12 章: 保管の読み書きを記録する
// ---------------------------------------------------------------------------

/// **保管の読み書きが記録される。**
///
/// ドメインは記録の手段を知りません。行き先を差し替えても、呼ぶ側は変わりません。
#[test]
fn reading_and_writing_are_recorded() {
    use std::rc::Rc;
    use zettai_step5_http::log_sink::Remembered;

    // `Rc` にする。`Remembered` は `RefCell` を持つので `Sync` ではない
    let sink = Rc::new(Remembered::default());
    let store = PostgresEventStore::connect(CONN, &unique_table("log"))
        .expect("DB が起動している")
        .recording_to(Box::new(SharedSink(sink.clone())));

    handle_in_transaction(
        &store,
        &uberto(),
        &book(),
        ToDoListCommand::CreateList { list_name: book() },
    )
    .expect("作れる");

    let lines = sink.lines();
    assert!(!lines.is_empty(), "何も記録されていない");
    assert!(
        lines.iter().any(|l| l.contains("イベントを追記した")),
        "実際: {lines:?}"
    );
    assert!(
        lines.iter().any(|l| l.contains(r#""user":"uberto""#)),
        "実際: {lines:?}"
    );
    store.drop_table().expect("片付けられる");
}

/// 同じ行き先を共有する。
struct SharedSink(std::rc::Rc<zettai_step5_http::log_sink::Remembered>);

impl zettai_step5_domain::logging::LogSink for SharedSink {
    fn record(&self, record: &zettai_step5_domain::logging::Record) {
        self.0.record(record);
    }
}
