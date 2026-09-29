//! 永続化のポート（第 9 章）。**契約だけを置く。**
//!
//! 実体は `zettai-step4-http` 側にある。ドメインは
//! **どこに保存するかを知りません**（[ADR-027](../../../../docs/adr/ADR-027-crate-boundary.md)）。
//!
//! 保存するのは**起きたことだけ**。状態は保存しない
//! （[ADR-008](../../../../docs/adr/ADR-008-event-store-single-table.md) を踏襲）。

use crate::{ListName, ToDoListEvent, User, ZettaiError};

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
