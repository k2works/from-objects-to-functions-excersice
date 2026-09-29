//! **ロールバックのテスト。** これが green にならない案は成立していない。
//!
//! 「読む → 判断する → 書く」の途中で失敗させ、**1 件も残らない**ことを確かめる。

use tx_shapes::a_scope::Transactional;
use tx_shapes::b_argument::StoreIn;
use tx_shapes::pg::{count, create_table, drop_table, ArgStore, LazyStore, ScopeStore};
use tx_shapes::{a_scope, b_argument, c_lazy, connect, unique_table, Error, Event};

/// 2 件書かせて、2 件目で落とす。
const THREE_EVENTS: [&str; 3] = ["a", "b", "c"];

fn setup(label: &str) -> String {
    let table = unique_table(label);
    let mut client = connect().expect("DB が起動している");
    create_table(&mut client, &table).expect("作れる");
    table
}

fn teardown(table: &str) {
    let mut client = connect().expect("DB が起動している");
    drop_table(&mut client, table);
}

fn rows(table: &str) -> i64 {
    let mut client = connect().expect("DB が起動している");
    count(&mut client, table)
}

// ---------------------------------------------------------------------------
// 案 A
// ---------------------------------------------------------------------------

#[test]
fn a_commits_when_everything_succeeds() {
    let table = setup("a_ok");
    let store = ScopeStore::new(connect().unwrap(), &table);

    a_scope::handle(&store, "k", "x").expect("通る");

    assert_eq!(rows(&table), 1);
    teardown(&table);
}

#[test]
fn a_leaves_nothing_when_it_fails_halfway() {
    let table = setup("a_ng");
    let mut store = ScopeStore::new(connect().unwrap(), &table);
    store.fail_at = Some(2); // 2 件目で落とす

    // 3 件書こうとして 2 件目で落ちる
    let result = store.in_transaction(&|tx| {
        let before = a_scope::TxStore::events_of(tx, "k")?;
        let events: Vec<Event> = THREE_EVENTS.iter().map(|s| Event(s.to_string())).collect();
        a_scope::TxStore::append(tx, "k", &events)?;
        Ok([before, events].concat())
    });

    assert!(matches!(result, Err(Error::Store(_))));
    assert_eq!(rows(&table), 0, "1 件も残らない");
    teardown(&table);
}

// ---------------------------------------------------------------------------
// 案 B
// ---------------------------------------------------------------------------

#[test]
fn b_commits_when_everything_succeeds() {
    let table = setup("b_ok");
    let store = ArgStore::new(&table);
    let mut client = connect().unwrap();
    let mut tx = client.transaction().unwrap();

    b_argument::handle(&store, &mut tx, "k", "x").expect("通る");
    tx.commit().expect("確定できる");

    assert_eq!(rows(&table), 1);
    teardown(&table);
}

#[test]
fn b_leaves_nothing_when_it_fails_halfway() {
    let table = setup("b_ng");
    let mut store = ArgStore::new(&table);
    store.fail_at = Some(2);
    let mut client = connect().unwrap();

    let result = {
        let mut tx = client.transaction().unwrap();
        let events: Vec<Event> = THREE_EVENTS.iter().map(|s| Event(s.to_string())).collect();
        let r = store.append(&mut tx, "k", &events);
        // **commit を呼ばない。** tx が drop されてロールバックする
        r
    };

    assert!(matches!(result, Err(Error::Store(_))));
    assert_eq!(rows(&table), 0, "1 件も残らない");
    teardown(&table);
}

// ---------------------------------------------------------------------------
// 案 C
// ---------------------------------------------------------------------------

#[test]
fn c_commits_when_everything_succeeds() {
    let table = setup("c_ok");
    let store = LazyStore::new(connect().unwrap(), &table);

    store.run(c_lazy::handle("k", "x")).expect("通る");

    assert_eq!(rows(&table), 1);
    teardown(&table);
}

#[test]
fn c_leaves_nothing_when_it_fails_halfway() {
    let table = setup("c_ng");
    let mut store = LazyStore::new(connect().unwrap(), &table);
    store.fail_at = Some(2);

    let events: Vec<Event> = THREE_EVENTS.iter().map(|s| Event(s.to_string())).collect();
    let result = store.run(c_lazy::persist("k", events));

    assert!(matches!(result, Err(Error::Store(_))));
    assert_eq!(rows(&table), 0, "1 件も残らない");
    teardown(&table);
}
