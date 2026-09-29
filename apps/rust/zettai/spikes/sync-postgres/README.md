# スパイク: 同期の postgres クレート（Unit 1・Unit 5）

```bash
docker compose up -d zettai-db
cargo run     # Unit 1: INSERT / SELECT / トランザクションが書けるか
cargo test    # Unit 5: エラーの包み方とテストの分離
```

## Unit 1（[ADR-024](../../../../docs/adr/ADR-024-no-async.md)）

同期の `postgres` クレートで `INSERT` / `SELECT` / トランザクションが書けた。
**async を第 9 章まで遅らせられる**という判断の根拠。

## Unit 5・スパイク 3: `postgres::Error` を自前の型に包めるか

**包める。`impl From` を 1 つ書けば `?` が変換する。**

ただし 1 つ踏んだ。

```text
詳細: db error
```

**`postgres::Error` の `Display` は `"db error"` としか出さない。**
本当の理由は `source()` の先にある。たどる関数（`detail_of`）を書いた。

```text
db error: ERROR: relation "table_that_does_not_exist" does not exist
```

**`Display` を自前で書くかどうか（持ち込み 3）は、これで向きが決まる。**
包む側が `source()` をたどって文字列にするので、包まれる側の `Display` に頼らない。

## Unit 5・スパイク 4: 並列テストで書き込み先を分ける

**スキーマごと分ける。** テーブル名を変えるだけでは `CREATE TABLE` が競る。

```rust
pub fn unique_schema(label: &str) -> String {
    use std::sync::atomic::{AtomicUsize, Ordering};
    static COUNTER: AtomicUsize = AtomicUsize::new(0);
    let n = COUNTER.fetch_add(1, Ordering::SeqCst);
    format!("zettai_{label}_{}_{n}", std::process::id())
}
```

3 つのテストを並列に走らせて、件数が混ざらないことを確かめた（3 件・7 件・1 件）。

なでしこ3 版 Unit 7 では記録先を 1 つに決め打ちし、**並列に走るテストが同じ場所に書いて件数が混ざった**。
