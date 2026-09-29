//! ドメイン（第 4 章の段階）。**別のクレートにしてある。**
//!
//! 第 3 章でインフラから分けた（Unit 2 のゲート 2）。クレートを分けると、
//! `Cargo.toml` に書いていない相手は**呼ぼうとした時点でコンパイルが止まる**。
//! 2 対象は境界検査を書いて守ったが、この版は書いていない。
//!
//! 第 4 章で `ToDoListHub` が入り、**依存を関数値として受け取る**ように
//! なった。どこから取り出しどこへ保存するかを、ドメインは知らない。
//!
//! このクレートは依存を 1 つも持たない。

/// ToDo リストの名前。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ListName(pub String);

impl ListName {
    pub fn new(name: &str) -> Self {
        ListName(name.to_string())
    }
}

/// 利用者。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct User(pub String);

impl User {
    pub fn new(name: &str) -> Self {
        User(name.to_string())
    }
}

/// ToDo 項目。第 6 章で状態が加わる。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ToDoItem {
    pub description: String,
}

impl ToDoItem {
    pub fn new(description: &str) -> Self {
        ToDoItem {
            description: description.to_string(),
        }
    }
}

/// ToDo リスト。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ToDoList {
    pub list_name: ListName,
    pub items: Vec<ToDoItem>,
}

/// 利用者のリストを取り出す（第 2 章から置いてある既定の中身）。
///
/// **見つからないことを `Option` で表す。** 第 7 章で `Result` に変わり、
/// 「なぜ見つからないか」を持つようになる。
///
/// 第 4 章からは、これは**ハブに渡す関数値の 1 つ**にすぎない。
/// 第 5 章でイベントの畳み込みになり、第 9 章で永続化される。
pub fn fetch_list(user: &User, list_name: &ListName) -> Option<ToDoList> {
    if user != &User::new("uberto") {
        return None;
    }
    match list_name.0.as_str() {
        "book" => Some(ToDoList {
            list_name: list_name.clone(),
            items: vec![
                ToDoItem::new("write chapter"),
                ToDoItem::new("insert code"),
                ToDoItem::new("publish book"),
            ],
        }),
        "shopping" => Some(ToDoList {
            list_name: list_name.clone(),
            items: vec![],
        }),
        _ => None,
    }
}

/// ユースケースの入口（第 4 章）。
///
/// **依存を関数値として受け取る。** どこから取り出し、どこへ保存するかを
/// ドメインは知らない。テストからは別の関数値を渡すだけで差し替えられる。
///
/// 型引数で持っているのは `impl Fn` と同じこと（静的ディスパッチ）。
/// **並びに入れるわけではないので、`Box` にしなくてよい**（[ADR-030]）。
/// 第 5 章のイベントの変換は事情が違い、そちらは `Box<dyn Fn>` になる。
pub struct ToDoListHub<F, S> {
    fetch: F,
    save: S,
}

impl<F, S> ToDoListHub<F, S>
where
    F: Fn(&User, &ListName) -> Option<ToDoList>,
    S: Fn(&User, &ToDoList),
{
    pub fn new(fetch: F, save: S) -> Self {
        ToDoListHub { fetch, save }
    }

    /// リストを見る。
    pub fn list_of(&self, user: &User, list_name: &ListName) -> Option<ToDoList> {
        (self.fetch)(user, list_name)
    }

    /// リストに項目を足す。**足した後のリストを返す。**
    ///
    /// 受け取ったリストを書き換えるのではなく、**足した新しいリストを作る**。
    /// もとのリストは変わらないので、第 5 章で「イベントを適用する関数」に
    /// そのまま化ける。
    pub fn add_item(&self, user: &User, list_name: &ListName, item: ToDoItem) -> Option<ToDoList> {
        let list = (self.fetch)(user, list_name)?;
        let updated = ToDoList {
            list_name: list.list_name,
            items: [list.items, vec![item]].concat(),
        };
        (self.save)(user, &updated);
        Some(updated)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_known_list_has_its_items() {
        let list = fetch_list(&User::new("uberto"), &ListName::new("book")).unwrap();
        assert_eq!(list.items.len(), 3);
        assert_eq!(list.list_name, ListName::new("book"));
    }

    #[test]
    fn an_empty_list_is_still_a_list() {
        let list = fetch_list(&User::new("uberto"), &ListName::new("shopping")).unwrap();
        assert!(list.items.is_empty());
    }

    #[test]
    fn an_unknown_user_has_nothing() {
        assert!(fetch_list(&User::new("nobody"), &ListName::new("book")).is_none());
    }

    // -----------------------------------------------------------------
    // 第 4 章: ハブは渡された関数値しか使わない
    // -----------------------------------------------------------------

    use std::cell::RefCell;

    #[test]
    fn the_hub_uses_the_function_it_was_given() {
        // 埋め込みのデータではなく、**ここで渡したものが返る**。
        let hub = ToDoListHub::new(
            |_u, name| {
                Some(ToDoList {
                    list_name: name.clone(),
                    items: vec![ToDoItem::new("渡したほう")],
                })
            },
            |_u, _l| {},
        );

        let list = hub
            .list_of(&User::new("uberto"), &ListName::new("book"))
            .unwrap();

        assert_eq!(list.items, vec![ToDoItem::new("渡したほう")]);
    }

    #[test]
    fn adding_an_item_saves_the_new_list() {
        let saved: RefCell<Vec<ToDoList>> = RefCell::new(Vec::new());
        let hub = ToDoListHub::new(
            |_u, name| {
                Some(ToDoList {
                    list_name: name.clone(),
                    items: vec![ToDoItem::new("先にあったもの")],
                })
            },
            |_u, list: &ToDoList| saved.borrow_mut().push(list.clone()),
        );

        let after = hub
            .add_item(
                &User::new("uberto"),
                &ListName::new("book"),
                ToDoItem::new("足したもの"),
            )
            .unwrap();

        assert_eq!(after.items.len(), 2, "足した分だけ増える");
        assert_eq!(saved.borrow().len(), 1, "保存が 1 回呼ばれる");
        assert_eq!(saved.borrow()[0], after, "保存されたのは足した後のリスト");
    }

    #[test]
    fn adding_to_a_missing_list_saves_nothing() {
        let saved: RefCell<usize> = RefCell::new(0);
        let hub = ToDoListHub::new(|_u, _n| None, |_u, _l: &ToDoList| *saved.borrow_mut() += 1);

        let after = hub.add_item(
            &User::new("uberto"),
            &ListName::new("nope"),
            ToDoItem::new("足せない"),
        );

        assert!(after.is_none());
        assert_eq!(*saved.borrow(), 0, "無いリストには保存しない");
    }
}
