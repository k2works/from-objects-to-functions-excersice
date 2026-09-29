//! PostgreSQL のイベントストア（第 9 章）。
//!
//! **1 テーブルに追記するだけ。状態を保存しない**
//! （[ADR-008](../../../../docs/adr/ADR-008-event-store-single-table.md) を踏襲）。
//!
//! `postgres` は**同期のクレート**です（[ADR-024](../../../../docs/adr/ADR-024-no-async.md)）。
//! `async` を採らない判断が、この章で試されます。

use postgres::{Client, NoTls};
use std::sync::Mutex;
use zettai_step4_domain::store::EventStore;
use zettai_step4_domain::{ListName, ToDoItem, ToDoListEvent, User, ZettaiError};

/// エラーの連鎖をたどって文字列にする。
///
/// **`postgres::Error` の `Display` は `"db error"` としか出さない。**
/// 本当の理由は `source()` の先にある（スパイクで踏んだ）。
fn detail_of(e: &(dyn std::error::Error + 'static)) -> String {
    let mut parts = vec![e.to_string()];
    let mut cause = e.source();
    while let Some(c) = cause {
        parts.push(c.to_string());
        cause = c.source();
    }
    parts.join(": ")
}

/// **境界で包む。** ドメインは `postgres` を知らない（[ADR-027]）。
fn unavailable(e: &postgres::Error) -> ZettaiError {
    ZettaiError::StoreUnavailable {
        detail: detail_of(e),
    }
}

/// イベント 1 つを JSON にする。
///
/// **自前で書く。** `serde_json` は依存 +5・ビルド +8.30 秒、
/// `serde` の derive まで入れると +11・+16.57 秒。
/// **書くものは 2 種類・各 1 フィールドで、30 行に収まる**
/// （[ADR-012](../../../../docs/adr/ADR-012-own-json-converter.md) を踏襲）。
///
/// 手で書くぶん、**往復の性質テストで守ります**。
pub fn to_json(event: &ToDoListEvent) -> String {
    match event {
        ToDoListEvent::ListCreated { list_name } => format!(
            r#"{{"type":"ListCreated","value":"{}"}}"#,
            escape(&list_name.0)
        ),
        ToDoListEvent::ItemAdded { item } => format!(
            r#"{{"type":"ItemAdded","value":"{}"}}"#,
            escape(&item.description)
        ),
    }
}

/// JSON からイベントに戻す。
pub fn from_json(text: &str) -> Result<ToDoListEvent, ZettaiError> {
    let kind = field(text, "type").ok_or_else(|| broken(text))?;
    let value = field(text, "value").ok_or_else(|| broken(text))?;
    match kind.as_str() {
        "ListCreated" => Ok(ToDoListEvent::ListCreated {
            list_name: ListName::new(&value),
        }),
        "ItemAdded" => Ok(ToDoListEvent::ItemAdded {
            item: ToDoItem::new(&value),
        }),
        _ => Err(broken(text)),
    }
}

fn broken(text: &str) -> ZettaiError {
    ZettaiError::StoreUnavailable {
        detail: format!("読めない記録: {text}"),
    }
}

/// `"key": "..."` の中身を取り出す。**キーは 2 つだけ。**
///
/// **`jsonb` は入れたままの形では返ってきません。** 正規化して
/// `{"type": "ListCreated", ...}` のように**コロンの後ろに空白**が入ります。
/// 自前で書いた最初の版は `"type":"` を探していて読めませんでした。
/// **単体の往復テストでは出ず、DB を通す結合テストだけが見つけました。**
fn field(text: &str, key: &str) -> Option<String> {
    let needle = format!("\"{key}\"");
    let start = text.find(&needle)? + needle.len();
    let rest = text[start..].trim_start();
    let rest = rest.strip_prefix(':')?.trim_start();
    let rest = rest.strip_prefix('"')?;
    let mut out = String::new();
    let mut chars = rest.chars();
    while let Some(c) = chars.next() {
        match c {
            '"' => return Some(out),
            '\\' => out.push(chars.next()?),
            _ => out.push(c),
        }
    }
    None
}

fn escape(s: &str) -> String {
    s.replace('\\', r"\\").replace('"', "\\\"")
}

/// PostgreSQL の保管。
pub struct PostgresEventStore {
    client: Mutex<Client>,
    table: String,
}

impl PostgresEventStore {
    /// つないで、テーブルが無ければ作る。
    ///
    /// **テーブル名を引数で受ける。** テストごとに分けるため
    /// （なでしこ3 版 Unit 7 で、記録先を 1 つに決め打ちして件数が混ざった）。
    pub fn connect(conn: &str, table: &str) -> Result<Self, ZettaiError> {
        let mut client = Client::connect(conn, NoTls).map_err(|e| unavailable(&e))?;
        client
            .batch_execute(&format!(
                "CREATE TABLE IF NOT EXISTS {table} (
                    id BIGSERIAL PRIMARY KEY,
                    user_name TEXT NOT NULL,
                    list_name TEXT NOT NULL,
                    payload JSONB NOT NULL,
                    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
                )"
            ))
            .map_err(|e| unavailable(&e))?;
        Ok(PostgresEventStore {
            client: Mutex::new(client),
            table: table.to_string(),
        })
    }

    /// 使っているテーブルの名前。
    pub fn table_name(&self) -> &str {
        &self.table
    }

    /// 片付ける（テスト用）。
    pub fn drop_table(&self) -> Result<(), ZettaiError> {
        self.client
            .lock()
            .expect("毒されていない")
            .batch_execute(&format!("DROP TABLE IF EXISTS {}", self.table))
            .map_err(|e| unavailable(&e))
    }
}

impl EventStore for PostgresEventStore {
    fn append(
        &self,
        user: &User,
        list_name: &ListName,
        events: &[ToDoListEvent],
    ) -> Result<(), ZettaiError> {
        let mut client = self.client.lock().expect("毒されていない");
        // **1 つのコマンドが生んだイベントは、まとめて入るか入らないか。**
        // 境界の話は第 10 章で詳しくやる。
        let mut tx = client.transaction().map_err(|e| unavailable(&e))?;
        for event in events {
            tx.execute(
                &format!(
                    "INSERT INTO {} (user_name, list_name, payload)
                     VALUES ($1, $2, ($3::text)::jsonb)",
                    self.table
                ),
                // `$3::jsonb` と書くとパラメータの型が jsonb と推論され、
                // &str を渡せない（既知の制約 1）。text で受けてからキャストする。
                &[&user.0, &list_name.0, &to_json(event)],
            )
            .map_err(|e| unavailable(&e))?;
        }
        tx.commit().map_err(|e| unavailable(&e))
    }

    fn events_of(
        &self,
        user: &User,
        list_name: &ListName,
    ) -> Result<Vec<ToDoListEvent>, ZettaiError> {
        let rows = self
            .client
            .lock()
            .expect("毒されていない")
            .query(
                &format!(
                    "SELECT payload::text FROM {} WHERE user_name = $1 AND list_name = $2
                     ORDER BY id",
                    self.table
                ),
                &[&user.0, &list_name.0],
            )
            .map_err(|e| unavailable(&e))?;

        rows.iter()
            .map(|row| from_json(row.get::<_, &str>(0)))
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_created_event_survives_the_round_trip() {
        let event = ToDoListEvent::ListCreated {
            list_name: ListName::new("book"),
        };
        assert_eq!(from_json(&to_json(&event)).unwrap(), event);
    }

    #[test]
    fn quotes_and_backslashes_survive() {
        let event = ToDoListEvent::ItemAdded {
            item: ToDoItem::new(r#"write "the\ book""#),
        };
        assert_eq!(from_json(&to_json(&event)).unwrap(), event);
    }

    /// **`jsonb` が正規化して返す形も読める。**
    ///
    /// この形を確かめていなかったので、最初の版は DB を通したときだけ
    /// 落ちました。**単体の往復テストは通っていました。**
    #[test]
    fn the_shape_postgres_returns_is_readable() {
        let normalized = r#"{"type": "ListCreated", "value": "book"}"#;
        assert_eq!(
            from_json(normalized).unwrap(),
            ToDoListEvent::ListCreated {
                list_name: ListName::new("book")
            }
        );
    }

    #[test]
    fn a_broken_record_is_a_failure() {
        assert!(matches!(
            from_json("{}"),
            Err(ZettaiError::StoreUnavailable { .. })
        ));
    }

    #[test]
    fn an_unknown_type_is_a_failure() {
        assert!(from_json(r#"{"type":"Nope","value":"x"}"#).is_err());
    }
}
