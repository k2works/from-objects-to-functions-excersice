//! 受け入れテストの入口（第 3 章）。
//!
//! シナリオは業務の言葉だけで書き、HTTP もドメインの関数名も出さない。
//! 「どうやるか」は経路が持つ。
//!
//! **既製品（`cucumber`）を先に調べた。** Gherkin の `.feature` ファイルを
//! 与えてくれるが、依存が 6 → 127 クレートに増え、ビルドが 92 秒伸びる。
//! `just check` の上限 20 秒を単独で超える。
//!
//! 連載が必要としているのは「経路の差し替え」で、それはトレイト 1 つで足りる。
//! **言語が与えるものを使う**（[ADR-028]）。

use crate::http::handle;
use crate::store::InMemoryLists;
use zettai_step4_domain::{ListName, ToDoItem, ToDoList, ToDoListHub, User, ZettaiError};

/// 保管の上に組み立てたハブ。**clippy に名前を付けろと言われた**
/// （`type_complexity`）。付けたほうが読みやすい。
pub type StoreHub<'a> = ToDoListHub<
    Box<dyn Fn(&User, &ListName) -> Result<ToDoList, ZettaiError> + 'a>,
    Box<dyn Fn(&User, &ToDoList) + 'a>,
>;

/// シナリオが使える操作。**経路ごとに実装する。**
///
/// Kotlin 版は Pesticide の `DdtActions`、なでしこ3 版は関数値を詰めた辞書。
/// Rust はトレイトがそのまま当たる。
pub trait ZettaiActions {
    /// 記録に残す経路の名前。どの経路で落ちたかを知るために要る。
    fn route(&self) -> &'static str;

    /// 利用者のリストの項目を、説明の並びで返す。無ければ `None`。
    fn items_of(&self, user: &str, list_name: &str) -> Option<Vec<String>>;

    /// リストに項目を足す（第 4 章）。
    ///
    /// **業務の言葉のまま。** どこに足すか、誰が持つかは経路が決める。
    fn add_item(&self, user: &str, list_name: &str, description: &str);
}

/// 経路 1: ドメインを直接呼ぶ。HTTP を通さない。
///
/// 第 4 章で保存先を持つようになった。**経路ごとに自分の保存先を持つ**ので、
/// 片方で足したものがもう片方に見えることはない。
#[derive(Default)]
pub struct DomainOnly {
    store: InMemoryLists,
}

impl DomainOnly {
    pub fn seeded() -> Self {
        DomainOnly {
            store: InMemoryLists::seeded(),
        }
    }

    /// 保存先からハブを組み立てる。**配線はここだけ。**
    fn hub(&self) -> StoreHub<'_> {
        ToDoListHub::new(
            Box::new(|user, name| self.store.fetch(user, name)),
            Box::new(|user, list| self.store.save(user, list)),
        )
    }
}

impl ZettaiActions for DomainOnly {
    fn route(&self) -> &'static str {
        "ドメイン直接"
    }

    fn items_of(&self, user: &str, list_name: &str) -> Option<Vec<String>> {
        let list = self
            .hub()
            .list_of(&User::new(user), &ListName::new(list_name))
            .ok()?;
        Some(descriptions(&list))
    }

    fn add_item(&self, user: &str, list_name: &str, description: &str) {
        // **`Result` を捨てると `unused_must_use` で止まる。**
        // `Option` のときは黙って捨てられていた（第 7 章のつまずき）。
        let outcome = self.hub().add_item(
            &User::new(user),
            &ListName::new(list_name),
            ToDoItem::new(description),
        );
        assert!(
            outcome.is_ok(),
            "{}: 項目を足せなかった: {outcome:?}",
            self.route()
        );
    }
}

/// 経路 2: HTTP のハンドラを通す。
///
/// **サーバを起こさない。** `handle` が `tiny_http` の型を返さないので、
/// 関数として呼べる（第 2 章）。なでしこ3 版はサーバの起動と停止を
/// `Makefile` が持つ必要があった。
#[derive(Default)]
pub struct ThroughHttp {
    store: InMemoryLists,
}

impl ThroughHttp {
    pub fn seeded() -> Self {
        ThroughHttp {
            store: InMemoryLists::seeded(),
        }
    }

    fn hub(&self) -> StoreHub<'_> {
        ToDoListHub::new(
            Box::new(|user, name| self.store.fetch(user, name)),
            Box::new(|user, list| self.store.save(user, list)),
        )
    }
}

impl ZettaiActions for ThroughHttp {
    fn route(&self) -> &'static str {
        "HTTP 経由"
    }

    fn items_of(&self, user: &str, list_name: &str) -> Option<Vec<String>> {
        let reply = handle(&self.hub(), "GET", &format!("/todo/{user}/{list_name}"), "");
        if reply.status != 200 {
            return None;
        }
        Some(extract_items(&reply.body))
    }

    fn add_item(&self, user: &str, list_name: &str, description: &str) {
        handle(
            &self.hub(),
            "POST",
            &format!("/todo/{user}/{list_name}"),
            &format!("description={}", description.replace(' ', "+")),
        );
    }
}

/// ToDo リストから説明だけを取り出す。
fn descriptions(list: &ToDoList) -> Vec<String> {
    list.items
        .iter()
        .map(|item| item.description.clone())
        .collect()
}

/// HTML から `<li>` の中身を取り出す。
fn extract_items(html: &str) -> Vec<String> {
    html.split("<li>")
        .skip(1)
        .filter_map(|part| part.split("</li>").next())
        .map(str::to_string)
        .collect()
}

/// 全経路。シナリオはこれを回す。
pub fn all_routes() -> Vec<Box<dyn ZettaiActions>> {
    vec![
        Box::new(DomainOnly::seeded()),
        Box::new(ThroughHttp::seeded()),
    ]
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn extract_items_reads_the_list() {
        assert_eq!(
            extract_items("<ul><li>a</li><li>b</li></ul>"),
            vec!["a", "b"]
        );
    }

    #[test]
    fn extract_items_of_an_empty_list_is_empty() {
        assert!(extract_items("<ul></ul>").is_empty());
    }
}
