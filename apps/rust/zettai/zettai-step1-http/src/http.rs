//! HTTP アダプタ。ドメインを知っているが、ドメインはこちらを知らない。
//!
//! `tiny_http` にルーティング機構は無いので、パスの分解は自前で書く
//! （[ADR-025]）。フレームワークが隠さないぶん、第 2 章の「矢印で設計する」が
//! そのままコードに出る。
//!
//! リクエスト → 取り出す → 描く → 応答。矢印が 3 本ある。

use crate::domain::{fetch_list, ListName, ToDoList, User};

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

/// リクエストのパスから応答を決める。**矢印はここで繋がる。**
pub fn handle(path: &str) -> Reply {
    let parts = split_path(path);
    let (user, list_name) = match parts.as_slice() {
        ["todo", user, list_name] => (User::new(user), ListName::new(list_name)),
        _ => return not_found(),
    };

    match fetch_list(&user, &list_name) {
        Some(list) => Reply {
            status: 200,
            body: render(&list),
        },
        None => not_found(),
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

    #[test]
    fn a_known_list_is_rendered() {
        let reply = handle("/todo/uberto/book");
        assert_eq!(reply.status, 200);
        assert!(reply.body.contains("<li>write chapter</li>"));
        assert!(reply.body.contains("<h1>book</h1>"));
    }

    #[test]
    fn an_empty_list_is_still_two_hundred() {
        let reply = handle("/todo/uberto/shopping");
        assert_eq!(reply.status, 200);
        assert!(!reply.body.contains("<li>"));
    }

    #[test]
    fn an_unknown_list_is_not_found() {
        assert_eq!(handle("/todo/uberto/nope").status, 404);
    }

    #[test]
    fn an_unknown_user_is_not_found() {
        assert_eq!(handle("/todo/nobody/book").status, 404);
    }

    #[test]
    fn a_short_path_is_not_found() {
        assert_eq!(handle("/todo").status, 404);
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
