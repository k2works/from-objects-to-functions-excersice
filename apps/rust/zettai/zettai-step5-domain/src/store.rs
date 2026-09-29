//! 永続化のポート（第 9 章）。**契約だけを置く。**
//!
//! 実体は `zettai-step4-http` 側にある。ドメインは
//! **どこに保存するかを知りません**（[ADR-027](../../../../docs/adr/ADR-027-crate-boundary.md)）。
//!
//! 保存するのは**起きたことだけ**。状態は保存しない
//! （[ADR-008](../../../../docs/adr/ADR-008-event-store-single-table.md) を踏襲）。

use crate::{ListName, ToDoList, ToDoListEvent, User, ZettaiError};

/// イベントの保管。**追記と読み出しだけ。**
///
/// 更新も削除もありません。起きたことは変わらないためです。
pub trait EventStore {
    /// 起きたことを追記する。
    fn append(
        &self,
        user: &User,
        list_name: &ListName,
        events: &[ToDoListEvent],
    ) -> Result<(), ZettaiError>;

    /// 起きたことを順に読み出す。
    fn events_of(
        &self,
        user: &User,
        list_name: &ListName,
    ) -> Result<Vec<ToDoListEvent>, ZettaiError>;
}

/// 保管から読み戻して、いまの状態を作る。
///
/// **読むたびに畳み込む。** 状態を保存していないので、
/// 第 5 章の `replay` がそのまま使えます。
pub fn load(
    store: &dyn EventStore,
    user: &User,
    list_name: &ListName,
) -> Result<crate::ToDoList, ZettaiError> {
    let events = store.events_of(user, list_name)?;
    if events.is_empty() {
        return Err(ZettaiError::ListNotFound {
            user: user.clone(),
            list_name: list_name.clone(),
        });
    }
    Ok(crate::replay(events))
}

/// コマンドを実行し、起きたことを保管に追記する。
///
/// **`?` が 3 回続きます。** 読む・決める・書くのどれが失敗しても、
/// 理由がそのまま呼び手に返ります。
pub fn handle_with_store(
    store: &dyn EventStore,
    user: &User,
    list_name: &ListName,
    command: crate::ToDoListCommand,
) -> Result<crate::ToDoList, ZettaiError> {
    let before = store.events_of(user, list_name)?;
    let new_events = crate::execute(user, list_name, &before, command)?;
    store.append(user, list_name, &new_events)?;

    let all = [before, new_events].concat();
    Ok(crate::replay(all))
}

/// トランザクションの中でだけ触れる口（第 10 章）。
///
/// **`EventStore` と同じ操作を持つが、意味が違う。** こちらは
/// 「**1 つの単位の中にいる**」ことが型で分かる。
pub trait TxEventStore {
    fn events_of(
        &self,
        user: &User,
        list_name: &ListName,
    ) -> Result<Vec<ToDoListEvent>, ZettaiError>;

    fn append(
        &self,
        user: &User,
        list_name: &ListName,
        events: &[ToDoListEvent],
    ) -> Result<(), ZettaiError>;
}

/// 単位の中でする仕事。**`&dyn Fn` にするのは並びに入れないため**
/// （[ADR-030] の「他の関数値と同じ入れ物に入るか」で言えば、入らない側）。
pub type UnitOfWork<'a> = &'a dyn Fn(&dyn TxEventStore) -> Result<ToDoList, ZettaiError>;

/// 単位を貸す側（第 10 章）。
///
/// **トランザクションを値として返しません。** 返そうとすると
/// 「ロック」と「ロックの中身を借りたトランザクション」が同じ箱に入り、
/// 自己参照になって書けません（E0515・E0505）。
///
/// 代わりに**区間として貸します**。借りている間だけ触れて、
/// 抜けたら確定するか、失敗ならすべて無かったことになります。
pub trait TransactionalStore {
    fn in_transaction(&self, work: UnitOfWork<'_>) -> Result<ToDoList, ZettaiError>;
}

/// コマンドを 1 つのトランザクションで処理する（第 10 章）。
///
/// **境界がこの関数に見えます。** 読む・判断する・書くが同じ区間にあり、
/// どこで失敗しても 1 件も残りません。
///
/// `commit` はここに出てきません。**呼び忘れようがない形**です。
pub fn handle_in_transaction(
    store: &dyn TransactionalStore,
    user: &User,
    list_name: &ListName,
    command: crate::ToDoListCommand,
) -> Result<ToDoList, ZettaiError> {
    store.in_transaction(&|tx| {
        let before = tx.events_of(user, list_name)?;
        let new_events = crate::execute(user, list_name, &before, command.clone())?;
        tx.append(user, list_name, &new_events)?;
        Ok(crate::replay([before, new_events].concat()))
    })
}

/// 単位の中で読み戻す。
pub fn load_in_transaction(
    store: &dyn TransactionalStore,
    user: &User,
    list_name: &ListName,
) -> Result<ToDoList, ZettaiError> {
    store.in_transaction(&|tx| {
        let events = tx.events_of(user, list_name)?;
        if events.is_empty() {
            return Err(ZettaiError::ListNotFound {
                user: user.clone(),
                list_name: list_name.clone(),
            });
        }
        Ok(crate::replay(events))
    })
}

/// 射影を単位の中で引く（第 10 章）。
///
/// **読む側も同じ区間を通します。** コマンド側とクエリ側は型が違いますが
/// （第 8 章）、**保管との付き合い方は同じ**です。
pub fn summary_in_transaction(
    store: &dyn TransactionalStore,
    user: &User,
    list_name: &ListName,
) -> Result<crate::projection::ListSummary, ZettaiError> {
    // `in_transaction` は `ToDoList` を返す形に固定してある（オブジェクト安全のため）。
    // **射影は畳み込み直す。** イベントを 2 度読まない。
    let list = load_in_transaction(store, user, list_name)?;
    Ok(crate::projection::ListSummary {
        list_name: list.list_name.0,
        item_count: list.items.len(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{ToDoItem, ToDoListCommand};
    use std::cell::RefCell;

    /// 記憶だけの保管。**契約を確かめるためのもの。**
    #[derive(Default)]
    struct InMemoryStore {
        events: RefCell<Vec<ToDoListEvent>>,
    }

    impl EventStore for InMemoryStore {
        fn append(
            &self,
            _user: &User,
            _list_name: &ListName,
            events: &[ToDoListEvent],
        ) -> Result<(), ZettaiError> {
            self.events.borrow_mut().extend_from_slice(events);
            Ok(())
        }

        fn events_of(
            &self,
            _user: &User,
            _list_name: &ListName,
        ) -> Result<Vec<ToDoListEvent>, ZettaiError> {
            Ok(self.events.borrow().clone())
        }
    }

    /// いつも落ちる保管。**失敗が呼び手まで届くことを確かめる。**
    struct BrokenStore;

    impl EventStore for BrokenStore {
        fn append(
            &self,
            _u: &User,
            _l: &ListName,
            _e: &[ToDoListEvent],
        ) -> Result<(), ZettaiError> {
            Err(ZettaiError::StoreUnavailable {
                detail: "つながらない".to_string(),
            })
        }
        fn events_of(&self, _u: &User, _l: &ListName) -> Result<Vec<ToDoListEvent>, ZettaiError> {
            Err(ZettaiError::StoreUnavailable {
                detail: "つながらない".to_string(),
            })
        }
    }

    fn uberto() -> User {
        User::new("uberto")
    }
    fn book() -> ListName {
        ListName::new("book")
    }

    #[test]
    fn creating_then_loading_gives_the_list() {
        let store = InMemoryStore::default();
        handle_with_store(
            &store,
            &uberto(),
            &book(),
            ToDoListCommand::CreateList { list_name: book() },
        )
        .expect("作れる");

        let list = load(&store, &uberto(), &book()).expect("読み戻せる");
        assert_eq!(list.list_name, book());
        assert!(list.items.is_empty());
    }

    #[test]
    fn adding_items_survives_a_reload() {
        let store = InMemoryStore::default();
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
                item: ToDoItem::new("write"),
            },
        )
        .expect("足せる");

        let list = load(&store, &uberto(), &book()).expect("読み戻せる");
        assert_eq!(list.items.len(), 1);
    }

    #[test]
    fn nothing_stored_means_not_found() {
        let store = InMemoryStore::default();
        assert!(matches!(
            load(&store, &uberto(), &book()),
            Err(ZettaiError::ListNotFound { .. })
        ));
    }

    #[test]
    fn a_broken_store_reaches_the_caller() {
        assert!(matches!(
            load(&BrokenStore, &uberto(), &book()),
            Err(ZettaiError::StoreUnavailable { .. })
        ));
    }

    // -----------------------------------------------------------------
    // 第 10 章: 単位の中で処理する
    // -----------------------------------------------------------------

    /// 記憶だけの単位。**失敗したら書いたものを捨てる。**
    #[derive(Default)]
    struct InMemoryTx {
        committed: RefCell<Vec<ToDoListEvent>>,
        /// 単位の中で書いたもの。**確定するまで `committed` に移さない。**
        pending: RefCell<Vec<ToDoListEvent>>,
        /// **わざと落とす。** n 件目の append で失敗する
        fail_at: Option<usize>,
        written: RefCell<usize>,
    }

    impl TxEventStore for InMemoryTx {
        fn events_of(&self, _u: &User, _l: &ListName) -> Result<Vec<ToDoListEvent>, ZettaiError> {
            let mut all = self.committed.borrow().clone();
            all.extend(self.pending.borrow().iter().cloned());
            Ok(all)
        }

        fn append(
            &self,
            _u: &User,
            _l: &ListName,
            events: &[ToDoListEvent],
        ) -> Result<(), ZettaiError> {
            for event in events {
                *self.written.borrow_mut() += 1;
                if Some(*self.written.borrow()) == self.fail_at {
                    return Err(ZettaiError::StoreUnavailable {
                        detail: "わざと落とした".to_string(),
                    });
                }
                self.pending.borrow_mut().push(event.clone());
            }
            Ok(())
        }
    }

    impl TransactionalStore for InMemoryTx {
        fn in_transaction(&self, work: UnitOfWork<'_>) -> Result<ToDoList, ZettaiError> {
            self.pending.borrow_mut().clear();
            match work(self) {
                Ok(list) => {
                    // 確定
                    let pending = self.pending.borrow().clone();
                    self.committed.borrow_mut().extend(pending);
                    self.pending.borrow_mut().clear();
                    Ok(list)
                }
                Err(e) => {
                    // **書いたものを捨てる**
                    self.pending.borrow_mut().clear();
                    Err(e)
                }
            }
        }
    }

    #[test]
    fn a_command_in_a_unit_is_committed() {
        let store = InMemoryTx::default();

        handle_in_transaction(
            &store,
            &uberto(),
            &book(),
            ToDoListCommand::CreateList { list_name: book() },
        )
        .expect("作れる");

        assert_eq!(store.committed.borrow().len(), 1);
    }

    #[test]
    fn nothing_is_left_when_it_fails_halfway() {
        let store = InMemoryTx {
            fail_at: Some(1),
            ..Default::default()
        };

        let result = handle_in_transaction(
            &store,
            &uberto(),
            &book(),
            ToDoListCommand::CreateList { list_name: book() },
        );

        assert!(matches!(result, Err(ZettaiError::StoreUnavailable { .. })));
        assert!(store.committed.borrow().is_empty(), "1 件も残らない");
    }

    #[test]
    fn a_rejected_command_in_a_unit_writes_nothing() {
        let store = InMemoryTx::default();

        let result = handle_in_transaction(
            &store,
            &uberto(),
            &book(),
            ToDoListCommand::AddItem {
                item: ToDoItem::new("write"),
            },
        );

        assert!(matches!(result, Err(ZettaiError::ListNotFound { .. })));
        assert!(store.committed.borrow().is_empty());
    }

    #[test]
    fn loading_in_a_unit_sees_what_was_committed() {
        let store = InMemoryTx::default();
        handle_in_transaction(
            &store,
            &uberto(),
            &book(),
            ToDoListCommand::CreateList { list_name: book() },
        )
        .expect("作れる");

        let list = load_in_transaction(&store, &uberto(), &book()).expect("読める");
        assert_eq!(list.list_name, book());
    }

    #[test]
    fn the_summary_comes_from_the_same_unit() {
        let store = InMemoryTx::default();
        handle_in_transaction(
            &store,
            &uberto(),
            &book(),
            ToDoListCommand::CreateList { list_name: book() },
        )
        .expect("作れる");
        handle_in_transaction(
            &store,
            &uberto(),
            &book(),
            ToDoListCommand::AddItem {
                item: ToDoItem::new("write"),
            },
        )
        .expect("足せる");

        let summary = summary_in_transaction(&store, &uberto(), &book()).expect("引ける");
        assert_eq!(summary.list_name, "book");
        assert_eq!(summary.item_count, 1);
    }

    #[test]
    fn a_rejected_command_is_not_appended() {
        let store = InMemoryStore::default();
        let result = handle_with_store(
            &store,
            &uberto(),
            &book(),
            ToDoListCommand::AddItem {
                item: ToDoItem::new("write"),
            },
        );
        assert!(matches!(result, Err(ZettaiError::ListNotFound { .. })));
        assert!(store.events.borrow().is_empty(), "断られたら書かない");
    }
}
