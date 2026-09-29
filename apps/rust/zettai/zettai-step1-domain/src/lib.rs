//! ドメイン。**別のクレートにしてある。**
//!
//! 第 3 章でインフラから分けた（Unit 2 のゲート 2）。クレートを分けると、
//! `Cargo.toml` に書いていない相手は**呼ぼうとした時点でコンパイルが止まる**。
//! 2 対象は境界検査を書いて守ったが、この版は書いていない。
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

/// ToDo 項目。第 4 章で期限と状態が加わる。
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

/// 利用者のリストを取り出す。
///
/// **見つからないことを `Option` で表す。** 第 7 章で `Result` に変わり、
/// 「なぜ見つからないか」を持つようになる。
///
/// 第 2 章の時点ではデータを埋め込んでいる。第 5 章でイベントの
/// 畳み込みになり、第 9 章で永続化される。
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
}
