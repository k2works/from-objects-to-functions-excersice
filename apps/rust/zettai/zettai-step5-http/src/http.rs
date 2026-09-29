//! HTTP アダプタ。ドメインを知っているが、ドメインはこちらを知らない。
//!
//! `tiny_http` にルーティング機構は無いので、パスの分解は自前で書く
//! （[ADR-025]）。フレームワークが隠さないぶん、第 2 章の「矢印で設計する」が
//! そのままコードに出る。
//!
//! リクエスト → 取り出す → 描く → 応答。矢印が 3 本ある。

use zettai_step5_domain::validation::valid_rename;
use zettai_step5_domain::{
    ListName, ToDoItem, ToDoList, ToDoListCommand, ToDoListHub, User, ZettaiError,
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
///
/// **第 11 章でテンプレート機構に移しました**（[ADR-036]）。第 10 章までは
/// `format!` の直書きで、**値を無害化していませんでした**。
pub fn render(list: &ToDoList) -> Result<String, ZettaiError> {
    crate::template::render_list(list)
}

/// リクエストから応答を決める。**矢印はここで繋がる。**
///
/// 第 4 章で引数にハブが増えた。**取り出し方も保存先もここは知らない。**
/// 型引数で受けているので呼び出しは静的に決まる（[ADR-030] の案 A）。
pub fn handle<F, S>(hub: &ToDoListHub<F, S>, method: &str, path: &str, body: &str) -> Reply
where
    F: Fn(&User, &ListName) -> Result<ToDoList, ZettaiError>,
    S: Fn(&User, &ToDoList),
{
    let parts = split_path(path);

    // **`/rename` で終わるパスかどうかを先に見る。**
    // なでしこ3 版は前方一致だけで振り分け、`/rename` 以外への POST まで
    // 名前変更として処理していた（Unit 6 で実際に踏んだ）。
    if let ["todo", user, list_name, "rename"] = parts.as_slice() {
        return handle_rename(method, user, list_name, body);
    }

    let (user, list_name) = match parts.as_slice() {
        ["todo", user, list_name] => (User::new(user), ListName::new(list_name)),
        _ => return not_found(),
    };

    match method {
        "GET" => match hub.list_of(&user, &list_name) {
            Ok(list) => ok(&list),
            Err(reason) => failed(reason),
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
                    Err(reason) => failed(reason),
                }
            }
        },
        _ => not_found(),
    }
}

/// フォームの本文からキーの値を取り出す。
///
/// **第 11 章でパーセントデコードを足しました。** 第 4 章の版は `+` しか
/// 戻しておらず、`%E8%AA%AD` がそのまま残っていました。ASCII の説明しか
/// 使っていなかったので、受け入れテストは通り続けていました
/// （既知の制約 12）。
///
/// **値の中の `&` と `=` は `%26` / `%3D` で届く**ので、先に分割してよい。
pub fn field_in(body: &str, key: &str) -> Option<String> {
    body.split('&')
        .filter_map(|pair| pair.split_once('='))
        .find(|(k, _)| *k == key)
        .map(|(_, value)| percent_decode(&value.replace('+', " ")))
        .filter(|value| !value.is_empty())
}

/// フォームの本文から `description` を取り出す。
pub fn description_in(body: &str) -> Option<String> {
    field_in(body, "description")
}

/// `%XX` を戻す。
///
/// **自前で書く。** 要るのはこれだけで、クレートを足す理由が無い。
/// 戻せないバイトの並びはそのまま返す（失敗にしない）。
pub fn percent_decode(text: &str) -> String {
    let bytes = text.as_bytes();
    let mut out: Vec<u8> = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' && i + 2 < bytes.len() {
            let hex = std::str::from_utf8(&bytes[i + 1..i + 3]).ok();
            if let Some(byte) = hex.and_then(|h| u8::from_str_radix(h, 16).ok()) {
                out.push(byte);
                i += 3;
                continue;
            }
        }
        out.push(bytes[i]);
        i += 1;
    }
    String::from_utf8_lossy(&out).into_owned()
}

/// リスト名の変更（第 11 章）。
///
/// **検証はドメインが持ちます。** ここは値を取り出して渡すだけです。
fn handle_rename(method: &str, user: &str, list_name: &str, body: &str) -> Reply {
    match method {
        // フォームを出す
        "GET" => Reply {
            status: 200,
            body: match crate::template::render_rename_form(user, list_name, &[]) {
                Ok(html) => html,
                Err(e) => return failed(e),
            },
        },
        "POST" => {
            let new_name = field_in(body, "newname").unwrap_or_default();
            match valid_rename(user, &new_name) {
                // **成功したら 302。** 新しい名前の画面へ送る
                Ok((_user, name)) => Reply {
                    status: 302,
                    body: format!("/todo/{}/{}", user, name.0),
                },
                // **断られたら理由をすべて並べて再表示する。**
                // 第 10 章までは 1 つしか返せなかった
                Err(reasons) => {
                    let texts: Vec<String> = reasons.iter().map(describe).collect();
                    match crate::template::render_rename_form(user, list_name, &texts) {
                        Ok(html) => Reply {
                            status: 400,
                            body: html,
                        },
                        Err(e) => failed(e),
                    }
                }
            }
        }
        _ => not_found(),
    }
}

fn ok(list: &ToDoList) -> Reply {
    match render(list) {
        Ok(body) => Reply { status: 200, body },
        Err(e) => failed(e),
    }
}

/// 断られた理由を、画面に出す言葉にする（第 11 章）。
///
/// **`match` が漏れを止めます。** 理由を足すとここも止まります。
pub fn describe(reason: &ZettaiError) -> String {
    match reason {
        ZettaiError::EmptyUserName => "利用者名を入れてください".to_string(),
        ZettaiError::EmptyListName => "新しい名前を入れてください".to_string(),
        ZettaiError::ListNameTooLong { limit } => {
            format!("新しい名前は {limit} 文字までです")
        }
        ZettaiError::ListNameHasMarkup => "新しい名前に記号は使えません".to_string(),
        ZettaiError::EmptyDescription => "説明を入れてください".to_string(),
        ZettaiError::ListNotFound { list_name, .. } => {
            format!("{} が見つかりません", list_name.0)
        }
        ZettaiError::ListAlreadyExists { list_name } => {
            format!("{} はもうあります", list_name.0)
        }
        ZettaiError::StoreUnavailable { .. } => "保管とやりとりできませんでした".to_string(),
    }
}

/// うまくいかなかった理由を状態コードに写す。
///
/// **理由が型なので、`match` が漏れを止める。** 理由を足したら
/// ここもコンパイルが止まる。第 7 章で理由が 1 つ増え、実際に止まった。
fn failed(reason: ZettaiError) -> Reply {
    match reason {
        ZettaiError::ListNotFound { .. } => not_found(),
        ZettaiError::ListAlreadyExists { .. } => Reply {
            status: 409,
            body: "<html><body><h1>409</h1></body></html>".to_string(),
        },
        ZettaiError::EmptyDescription => bad_request(),
        // 第 11 章で理由が 3 つ増え、**またここが止まった**（5 回目）。
        ZettaiError::EmptyUserName
        | ZettaiError::EmptyListName
        | ZettaiError::ListNameTooLong { .. }
        | ZettaiError::ListNameHasMarkup => bad_request(),
        // 第 9 章で理由が 1 つ増え、**またここが止まった**（3 回目）。
        ZettaiError::StoreUnavailable { .. } => Reply {
            status: 503,
            body: "<html><body><h1>503</h1></body></html>".to_string(),
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
    fn hub_over(store: &InMemoryLists) -> crate::acceptance::StoreHub<'_> {
        ToDoListHub::new(
            Box::new(|user, name| store.fetch(user, name)),
            Box::new(|user, list| store.save(user, list)),
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

    /// **`tiny_http` が実際に渡す形。** 自分で組み立てた文字列だけで
    /// 確かめない（第 9 章の `jsonb` の学び）。
    #[test]
    fn a_real_form_body_is_decoded() {
        // curl -X POST --data-urlencode 'description=読む 本&"<x>"' が送ってくる形
        let real = "description=%E8%AA%AD%E3%82%80+%E6%9C%AC%26%22%3Cx%3E%22";
        assert_eq!(description_in(real).unwrap(), r#"読む 本&"<x>""#);
    }

    #[test]
    fn a_value_containing_an_ampersand_survives_splitting() {
        // 値の中の & は %26 で届くので、分割してから戻す順序で正しい
        let body = "user=a%26b&newname=c";
        assert_eq!(field_in(body, "user").unwrap(), "a&b");
        assert_eq!(field_in(body, "newname").unwrap(), "c");
    }

    #[test]
    fn a_broken_percent_is_left_alone() {
        assert_eq!(percent_decode("100%"), "100%");
        assert_eq!(percent_decode("%zz"), "%zz");
    }

    /// **`/rename` 以外への POST は名前変更にならない。**
    ///
    /// なでしこ3 版は前方一致だけで振り分け、`/todo/{user}/{list}` への POST まで
    /// 名前変更として処理していた（Unit 6 で実際に踏んだ）。
    #[test]
    fn a_post_that_is_not_rename_is_not_a_rename() {
        let store = InMemoryLists::seeded();
        let reply = handle(
            &hub_over(&store),
            "POST",
            "/todo/uberto/book",
            "newname=reading",
        );

        // 名前変更なら 302 か 400。ここは項目の追加として扱われ、
        // `description` が無いので 400 になる
        assert_ne!(reply.status, 302, "名前変更として処理されている");
        assert_eq!(reply.status, 400);
        assert!(
            store
                .fetch(&User::new("uberto"), &ListName::new("reading"))
                .is_err(),
            "reading というリストができてしまっている"
        );
    }

    #[test]
    fn a_rename_form_is_shown() {
        let store = InMemoryLists::seeded();
        let reply = handle(&hub_over(&store), "GET", "/todo/uberto/book/rename", "");
        assert_eq!(reply.status, 200);
        assert!(reply.body.contains(r#"name="newname""#));
    }

    #[test]
    fn a_good_rename_redirects() {
        let store = InMemoryLists::seeded();
        let reply = handle(
            &hub_over(&store),
            "POST",
            "/todo/uberto/book/rename",
            "newname=reading",
        );
        assert_eq!(reply.status, 302);
        assert_eq!(reply.body, "/todo/uberto/reading");
    }

    #[test]
    fn a_bad_rename_shows_every_reason() {
        let store = InMemoryLists::seeded();
        let bad = "%3Cscript%3E".repeat(6); // 48 文字 かつ 記号
        let reply = handle(
            &hub_over(&store),
            "POST",
            "/todo/uberto/book/rename",
            &format!("newname={bad}"),
        );
        assert_eq!(reply.status, 400);
        assert!(reply.body.contains("40 文字までです"));
        assert!(reply.body.contains("記号は使えません"));
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
