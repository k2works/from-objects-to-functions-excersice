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
use zettai_step4_domain::store::{handle_with_store, load, EventStore};
use zettai_step4_domain::{ListName, ToDoItem, ToDoListCommand, User, ZettaiError};
use zettai_step4_http::event_store::PostgresEventStore;

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
    use zettai_step4_http::acceptance::{ThroughStore, ZettaiActions};

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
