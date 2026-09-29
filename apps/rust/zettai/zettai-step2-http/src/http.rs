//! HTTP アダプタ。ドメインを知っているが、ドメインはこちらを知らない。
//!
//! `tiny_http` にルーティング機構は無いので、パスの分解は自前で書く
//! （[ADR-025]）。フレームワークが隠さないぶん、第 2 章の「矢印で設計する」が
//! そのままコードに出る。
//!
//! リクエスト → 取り出す → 描く → 応答。矢印が 3 本ある。

use zettai_step2_domain::{
    ListName, Rejected, ToDoItem, ToDoList, ToDoListCommand, ToDoListHub, User,
};

/// 応答。状態コードと本文だけを持つ。
///
/// `tiny_http` の型を返さないのは、**この関数を HTTP 無しでテストできる**
/// ようにするため。サーバの組み立ては `src/bin/zettai.rs` が持つ。
#[derive(Debug, PartialEq, Eq)]
pub struct Reply {
    pub status: u16,
    pub body: String,
}

/// パスを `/` で割り、空を落とす。
///
/// `tiny_http` は前方一致もしないので、ここで全部を決める。
pub fn split_path(path: &str) -> Vec<&str> {
    path.split('/').filter(|part| !part.is_empty()).collect()
}

/// ToDo リストを HTML にする。
pub fn render(list: &ToDoList) -> String {
    let items: String = list
        .items
        .iter()
        .map(|item| format!("<li>{}</li>", item.description))
        .collect();
    format!(
        "<html><body><h1>{}</h1><ul>{}</ul></body></html>",
        list.list_name.0, items
    )
}

/// リクエストから応答を決める。**矢印はここで繋がる。**
///
/// 第 4 章で引数にハブが増えた。**取り出し方も保存先もここは知らない。**
/// 型引数で受けているので呼び出しは静的に決まる（[ADR-030] の案 A）。
pub fn handle<F, S>(hub: &ToDoListHub<F, S>, method: &str, path: &str, body: &str) -> Reply
where
    F: Fn(&User, &ListName) -> Option<ToDoList>,
    S: Fn(&User, &ToDoList),
{
    let parts = split_path(path);
    let (user, list_name) = match parts.as_slice() {
        ["todo", user, list_name] => (User::new(user), ListName::new(list_name)),
        _ => return not_found(),
    };

    match method {
        "GET" => match hub.list_of(&user, &list_name) {
            Some(list) => ok(&list),
            None => not_found(),
        },
        // 第 6 章でコマンドを通すようになった。**断られた理由が型で返る。**
        "POST" => match description_in(body) {
            None => bad_request(),
            Some(description) => {
                let command = ToDoListCommand::AddItem {
                    item: ToDoItem::new(&description),
                };
                match hub.handle(&user, &list_name, command) {
                    Ok(list) => ok(&list),
                    Err(reason) => rejected(reason),
                }
            }
        },
        _ => not_found(),
    }
}

/// フォームの本文から `description` を取り出す。
///
/// **自前で書く。** 要るのは 1 つのキーだけで、クレートを足す理由が無い。
pub fn description_in(body: &str) -> Option<String> {
    body.split('&')
        .filter_map(|pair| pair.split_once('='))
        .find(|(key, _)| *key == "description")
        .map(|(_, value)| value.replace('+', " "))
        .filter(|value| !value.is_empty())
}

fn ok(list: &ToDoList) -> Reply {
    Reply {
        status: 200,
        body: render(list),
    }
}

/// 断られた理由を状態コードに写す。
///
/// **理由が型なので、`match` が漏れを止める。** 理由を足したら
/// ここもコンパイルが止まる。
fn rejected(reason: Rejected) -> Reply {
    match reason {
        Rejected::ListDoesNotExist => not_found(),
        Rejected::ListAlreadyExists => Reply {
            status: 409,
            body: "<html><body><h1>409</h1></body></html>".to_string(),
        },
    }
}

fn bad_request() -> Reply {
    Reply {
        status: 400,
        body: "<html><body><h1>400</h1></body></html>".to_string(),
    }
}

fn not_found() -> Reply {
    Reply {
        status: 404,
        body: "<html><body><h1>404</h1></body></html>".to_string(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::store::InMemoryLists;

    /// テスト用の配線。**毎回まっさらな保存先を作る。**
    fn hub_over(
        store: &InMemoryLists,
    ) -> ToDoListHub<
        impl Fn(&User, &ListName) -> Option<ToDoList> + '_,
        impl Fn(&User, &ToDoList) + '_,
    > {
        ToDoListHub::new(
            |user, name| store.fetch(user, name),
            |user, list| store.save(user, list),
        )
    }

    fn get(store: &InMemoryLists, path: &str) -> Reply {
        handle(&hub_over(store), "GET", path, "")
    }

    #[test]
    fn a_known_list_is_rendered() {
        let reply = get(&InMemoryLists::seeded(), "/todo/uberto/book");
        assert_eq!(reply.status, 200);
        assert!(reply.body.contains("<li>write chapter</li>"));
        assert!(reply.body.contains("<h1>book</h1>"));
    }

    #[test]
    fn an_empty_list_is_still_two_hundred() {
        let reply = get(&InMemoryLists::seeded(), "/todo/uberto/shopping");
        assert_eq!(reply.status, 200);
        assert!(!reply.body.contains("<li>"));
    }

    #[test]
    fn an_unknown_list_is_not_found() {
        assert_eq!(
            get(&InMemoryLists::seeded(), "/todo/uberto/nope").status,
            404
        );
    }

    #[test]
    fn an_unknown_user_is_not_found() {
        assert_eq!(
            get(&InMemoryLists::seeded(), "/todo/nobody/book").status,
            404
        );
    }

    #[test]
    fn a_short_path_is_not_found() {
        assert_eq!(get(&InMemoryLists::seeded(), "/todo").status, 404);
    }

    #[test]
    fn split_path_drops_empty_parts() {
        assert_eq!(
            split_path("/todo/uberto/book"),
            vec!["todo", "uberto", "book"]
        );
        assert_eq!(split_path("/"), Vec::<&str>::new());
    }
}
