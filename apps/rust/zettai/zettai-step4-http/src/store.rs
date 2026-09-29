//! 保存先（第 4 章）。**インメモリ。** 第 9 章で PostgreSQL に変わる。
//!
//! ここが「関数値としてハブに渡されるもの」の実体になる。
//! ドメインはこのファイルを知らない（`Cargo.toml` に向きが書いてある）。

use std::collections::HashMap;
use std::sync::Mutex;
use zettai_step4_domain::{fetch_list, ListName, ToDoList, User, ZettaiError};

/// 利用者ごとのリストを持つ。
///
/// `Mutex` を使うのは、テストが並列に走るためではなく、
/// **`&self` のまま中身を書き換えるため**。ハブに渡す `save` は `Fn` で、
/// `FnMut` ではない（並びに入れられる形に揃えてある）。
#[derive(Default)]
pub struct InMemoryLists {
    lists: Mutex<HashMap<(String, String), ToDoList>>,
}

impl InMemoryLists {
    /// 第 2 章から置いてある既定の中身を入れて始める。
    pub fn seeded() -> Self {
        let store = InMemoryLists::default();
        let uberto = User::new("uberto");
        for name in ["book", "shopping"] {
            let list_name = ListName::new(name);
            if let Ok(list) = fetch_list(&uberto, &list_name) {
                store.save(&uberto, &list);
            }
        }
        store
    }

    /// ハブに渡す `fetch` の実体。
    ///
    /// **第 7 章で `Option` から `Result` に変わった。** 無いことを
    /// 「値が無い」ではなく「**誰のどのリストが**無いか」で返す。
    pub fn fetch(&self, user: &User, list_name: &ListName) -> Result<ToDoList, ZettaiError> {
        self.lists
            .lock()
            .expect("毒されていない")
            .get(&(user.0.clone(), list_name.0.clone()))
            .cloned()
            .ok_or_else(|| ZettaiError::ListNotFound {
                user: user.clone(),
                list_name: list_name.clone(),
            })
    }

    /// ハブに渡す `save` の実体。
    pub fn save(&self, user: &User, list: &ToDoList) {
        self.lists
            .lock()
            .expect("毒されていない")
            .insert((user.0.clone(), list.list_name.0.clone()), list.clone());
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use zettai_step4_domain::ToDoItem;

    #[test]
    fn a_seeded_store_has_the_book_list() {
        let store = InMemoryLists::seeded();
        let list = store
            .fetch(&User::new("uberto"), &ListName::new("book"))
            .expect("既定の中身が入っている");
        assert_eq!(list.items.len(), 3);
    }

    #[test]
    fn saving_replaces_what_was_there() {
        let store = InMemoryLists::seeded();
        let user = User::new("uberto");
        let name = ListName::new("book");
        store.save(
            &user,
            &ToDoList {
                list_name: name.clone(),
                items: vec![ToDoItem::new("だけ")],
            },
        );
        assert_eq!(store.fetch(&user, &name).unwrap().items.len(), 1);
    }

    #[test]
    fn an_unknown_list_is_not_there() {
        let store = InMemoryLists::seeded();
        assert!(store
            .fetch(&User::new("uberto"), &ListName::new("nope"))
            .is_err());
    }
}
