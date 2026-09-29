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
use zettai_step5_domain::{ListName, ToDoItem, ToDoList, ToDoListHub, User, ZettaiError};

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

    /// リストの名前を変える（第 11 章）。
    ///
    /// 断られたら**理由をすべて**返す。第 10 章までは 1 つしか返せなかった。
    fn rename(&self, user: &str, list_name: &str, new_name: &str) -> Result<(), Vec<String>>;
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

    /// 経路 1 は**検証だけ**を通す。HTTP のパスを知らない。
    fn rename(&self, user: &str, list_name: &str, new_name: &str) -> Result<(), Vec<String>> {
        let (_user, name) =
            zettai_step5_domain::validation::valid_rename(user, new_name).map_err(|reasons| {
                reasons
                    .iter()
                    .map(crate::http::describe)
                    .collect::<Vec<_>>()
            })?;
        let list = self
            .store
            .fetch(&User::new(user), &ListName::new(list_name))
            .map_err(|e| vec![crate::http::describe(&e)])?;
        self.store.save(
            &User::new(user),
            &ToDoList {
                list_name: name,
                items: list.items,
            },
        );
        Ok(())
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

    /// 経路 2 は**パスを通る**。`/rename` の判定がここで効く。
    fn rename(&self, user: &str, list_name: &str, new_name: &str) -> Result<(), Vec<String>> {
        let reply = handle(
            &self.hub(),
            "POST",
            &format!("/todo/{user}/{list_name}/rename"),
            &format!("newname={}", encode(new_name)),
        );
        if reply.status == 302 {
            // 302 の行き先にリストを移す（この経路は保管を持たないので写す）
            let list = self
                .store
                .fetch(&User::new(user), &ListName::new(list_name))
                .map_err(|e| vec![crate::http::describe(&e)])?;
            self.store.save(
                &User::new(user),
                &ToDoList {
                    list_name: ListName::new(new_name),
                    items: list.items,
                },
            );
            return Ok(());
        }
        // 400 の本文から理由を拾う
        Err(reasons_in(&reply.body))
    }
}

/// フォームに載せる形にする。**空白は `+`、それ以外は `%XX`。**
fn encode(text: &str) -> String {
    text.bytes()
        .map(|b| match b {
            b' ' => "+".to_string(),
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                (b as char).to_string()
            }
            _ => format!("%{b:02X}"),
        })
        .collect()
}

/// 画面から理由を拾う。
fn reasons_in(html: &str) -> Vec<String> {
    html.split(r#"<p class="error">"#)
        .skip(1)
        .filter_map(|part| part.split("</p>").next())
        .map(str::to_string)
        .collect()
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

/// 経路 3: 保管を通す（第 9 章）。**`db` フィーチャのときだけ。**
///
/// 2 経路では捕まらない欠陥がある、というのが 3 経路目を足す理由
/// （[ADR-020](../../../../docs/adr/ADR-020-third-route.md)）。
/// なでしこ3 版では経路固有の実装欠陥を実際に捕まえた。
#[cfg(feature = "db")]
pub struct ThroughStore {
    store: crate::event_store::PostgresEventStore,
}

#[cfg(feature = "db")]
impl ThroughStore {
    pub fn seeded(conn: &str, table: &str) -> Self {
        let store = crate::event_store::PostgresEventStore::connect(conn, table)
            .expect("DB が起動している");
        // 既定の中身を、**起きたこととして**入れる。
        for name in ["book", "shopping"] {
            let list_name = ListName::new(name);
            let uberto = User::new("uberto");
            let mut events = vec![zettai_step5_domain::ToDoListEvent::ListCreated {
                list_name: list_name.clone(),
            }];
            if name == "book" {
                for d in ["write chapter", "insert code", "publish book"] {
                    events.push(zettai_step5_domain::ToDoListEvent::ItemAdded {
                        item: ToDoItem::new(d),
                    });
                }
            }
            zettai_step5_domain::store::EventStore::append(&store, &uberto, &list_name, &events)
                .expect("入れられる");
        }
        ThroughStore { store }
    }

    pub fn drop_table(&self) {
        self.store.drop_table().expect("片付けられる");
    }
}

#[cfg(feature = "db")]
impl ZettaiActions for ThroughStore {
    fn route(&self) -> &'static str {
        "保管経由"
    }

    fn items_of(&self, user: &str, list_name: &str) -> Option<Vec<String>> {
        let list = zettai_step5_domain::store::load(
            &self.store,
            &User::new(user),
            &ListName::new(list_name),
        )
        .ok()?;
        Some(descriptions(&list))
    }

    fn add_item(&self, user: &str, list_name: &str, description: &str) {
        let outcome = zettai_step5_domain::store::handle_with_store(
            &self.store,
            &User::new(user),
            &ListName::new(list_name),
            zettai_step5_domain::ToDoListCommand::AddItem {
                item: ToDoItem::new(description),
            },
        );
        assert!(
            outcome.is_ok(),
            "{}: 項目を足せなかった: {outcome:?}",
            self.route()
        );
    }

    /// 経路 3 は**保管を通る**。検証を通ったら、起きたことを積み直す。
    fn rename(&self, user: &str, list_name: &str, new_name: &str) -> Result<(), Vec<String>> {
        let (_user, name) =
            zettai_step5_domain::validation::valid_rename(user, new_name).map_err(|reasons| {
                reasons
                    .iter()
                    .map(crate::http::describe)
                    .collect::<Vec<_>>()
            })?;

        let list = zettai_step5_domain::store::load(
            &self.store,
            &User::new(user),
            &ListName::new(list_name),
        )
        .map_err(|e| vec![crate::http::describe(&e)])?;

        // 新しい名前で作り直す。**起きたことは消さない。**
        let mut events = vec![zettai_step5_domain::ToDoListEvent::ListCreated {
            list_name: name.clone(),
        }];
        for item in list.items {
            events.push(zettai_step5_domain::ToDoListEvent::ItemAdded { item });
        }
        zettai_step5_domain::store::EventStore::append(
            &self.store,
            &User::new(user),
            &name,
            &events,
        )
        .map_err(|e| vec![crate::http::describe(&e)])?;
        Ok(())
    }
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
