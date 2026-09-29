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
use zettai_step1_domain::{fetch_list, ListName, User};

/// シナリオが使える操作。**経路ごとに実装する。**
///
/// Kotlin 版は Pesticide の `DdtActions`、なでしこ3 版は関数値を詰めた辞書。
/// Rust はトレイトがそのまま当たる。
pub trait ZettaiActions {
    /// 記録に残す経路の名前。どの経路で落ちたかを知るために要る。
    fn route(&self) -> &'static str;

    /// 利用者のリストの項目を、説明の並びで返す。無ければ `None`。
    fn items_of(&self, user: &str, list_name: &str) -> Option<Vec<String>>;
}

/// 経路 1: ドメインを直接呼ぶ。HTTP を通さない。
pub struct DomainOnly;

impl ZettaiActions for DomainOnly {
    fn route(&self) -> &'static str {
        "ドメイン直接"
    }

    fn items_of(&self, user: &str, list_name: &str) -> Option<Vec<String>> {
        let list = fetch_list(&User::new(user), &ListName::new(list_name))?;
        Some(
            list.items
                .iter()
                .map(|item| item.description.clone())
                .collect(),
        )
    }
}

/// 経路 2: HTTP のハンドラを通す。
///
/// **サーバを起こさない。** `handle` が `tiny_http` の型を返さないので、
/// 関数として呼べる（第 2 章）。なでしこ3 版はサーバの起動と停止を
/// `Makefile` が持つ必要があった。
pub struct ThroughHttp;

impl ZettaiActions for ThroughHttp {
    fn route(&self) -> &'static str {
        "HTTP 経由"
    }

    fn items_of(&self, user: &str, list_name: &str) -> Option<Vec<String>> {
        let reply = handle(&format!("/todo/{user}/{list_name}"));
        if reply.status != 200 {
            return None;
        }
        Some(extract_items(&reply.body))
    }
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
    vec![Box::new(DomainOnly), Box::new(ThroughHttp)]
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
