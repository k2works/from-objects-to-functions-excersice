//! 追加のスパイク（Unit 5）。
//!
//! - スパイク 3: `postgres::Error` を自前の `enum` に包めるか。`Display` が要るか
//! - スパイク 4: `cargo test` の並列実行で、テストごとに書き込み先を分ける手段

use postgres::{Client, NoTls};

pub const CONN: &str = "host=localhost user=zettai password=zettai dbname=zettai";

/// 自前の失敗の型（[ADR-031] の形を真似る）。
#[derive(Debug)]
pub enum StoreError {
    /// 接続できない。
    Unreachable { detail: String },
    /// 問い合わせが失敗した。
    QueryFailed { detail: String },
}

/// **`postgres::Error` を包む。** `impl From` を 1 つ書けば `?` が変換する。
///
/// 中身は文字列にする。`postgres::Error` をそのまま持つと、
/// **ドメインのクレートが `postgres` に依存することになる**（ADR-027 違反）。
///
/// **`e.to_string()` だけでは `"db error"` としか出ない。**
/// 中身は `source()` の先にある。
impl From<postgres::Error> for StoreError {
    fn from(e: postgres::Error) -> Self {
        StoreError::QueryFailed {
            detail: detail_of(&e),
        }
    }
}

/// エラーの連鎖をたどって文字列にする。
///
/// `postgres::Error` の `Display` は `"db error"` で、
/// **本当の理由は `source()` の先にある**（スパイク 3 で踏んだ）。
pub fn detail_of(e: &(dyn std::error::Error + 'static)) -> String {
    let mut parts = vec![e.to_string()];
    let mut cause = e.source();
    while let Some(c) = cause {
        parts.push(c.to_string());
        cause = c.source();
    }
    parts.join(": ")
}

/// テストごとの書き込み先。**スキーマを分ける。**
///
/// なでしこ3 版 Unit 7 では記録先を 1 つに決め打ちし、並列に走るテストが
/// 同じ場所に書いて件数が混ざった。テーブル名を変えるだけでは
/// `CREATE TABLE` が競るので、**スキーマごと分ける**。
pub fn unique_schema(label: &str) -> String {
    use std::sync::atomic::{AtomicUsize, Ordering};
    static COUNTER: AtomicUsize = AtomicUsize::new(0);
    let n = COUNTER.fetch_add(1, Ordering::SeqCst);
    format!("zettai_{label}_{}_{n}", std::process::id())
}

pub fn connect() -> Result<Client, StoreError> {
    Client::connect(CONN, NoTls).map_err(|e| StoreError::Unreachable {
        detail: e.to_string(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn with_schema(label: &str) -> (Client, String) {
        let mut client = connect().expect("DB が起動している");
        let schema = unique_schema(label);
        client
            .batch_execute(&format!(
                "CREATE SCHEMA {schema};
                 CREATE TABLE {schema}.events (id BIGSERIAL PRIMARY KEY, payload JSONB NOT NULL)"
            ))
            .expect("スキーマを作れる");
        (client, schema)
    }

    fn drop_schema(client: &mut Client, schema: &str) {
        client
            .batch_execute(&format!("DROP SCHEMA {schema} CASCADE"))
            .expect("片付けられる");
    }

    /// 2 つのテストが並列に走っても件数が混ざらない。
    fn insert_and_count(label: &str, rows: usize) -> i64 {
        let (mut client, schema) = with_schema(label);
        for _ in 0..rows {
            client
                .execute(
                    &format!("INSERT INTO {schema}.events (payload) VALUES (($1::text)::jsonb)"),
                    &[&r#"{"type":"ItemAdded"}"#],
                )
                .expect("入れられる");
        }
        let n: i64 = client
            .query_one(&format!("SELECT count(*) FROM {schema}.events"), &[])
            .expect("数えられる")
            .get(0);
        drop_schema(&mut client, &schema);
        n
    }

    #[test]
    fn one_writer_sees_only_its_own_rows() {
        assert_eq!(insert_and_count("a", 3), 3);
    }

    #[test]
    fn another_writer_sees_only_its_own_rows() {
        assert_eq!(insert_and_count("b", 7), 7);
    }

    #[test]
    fn a_third_writer_too() {
        assert_eq!(insert_and_count("c", 1), 1);
    }

    /// `postgres::Error` が `?` で自前の型に変換される。
    #[test]
    fn a_postgres_error_becomes_our_own() {
        fn broken() -> Result<(), StoreError> {
            let mut client = connect()?;
            client.batch_execute("SELECT * FROM table_that_does_not_exist")?;
            Ok(())
        }
        match broken() {
            Err(StoreError::QueryFailed { detail }) => {
                assert!(detail.contains("does not exist"), "詳細: {detail}");
            }
            other => panic!("想定と違う: {other:?}"),
        }
    }
}
