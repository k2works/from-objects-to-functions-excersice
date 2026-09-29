---
type: Article
title: "第 9 章 モナドによる安全なデータ永続化"
description: "Zettai 連載 Rust 版の第 9 章。同期の postgres クレートで永続化し、async を採らなかった判断が持ちこたえるかを見る。DB の検査を別ジョブに分けた理由を実測で示し、自前の JSON が jsonb の正規化で落ちた話も書く。"
tags: [article, zettai, rust, chapter]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-29T05:08:41Z }
---

# 第 9 章 モナドによる安全なデータ永続化

ここまで、項目はメモリの中にしかありません。**プロセスを止めると消えます。**

この章で保存します。そして、**第 1 章で「`async` を採らない」と決めた判断が試されます**（[ADR-024](../../../adr/ADR-024-no-async.md)）。

## 安全に永続化する

保存するのは**起きたことだけ**です。状態は保存しません（[ADR-008](../../../adr/ADR-008-event-store-single-table.md) を踏襲）。

ポートはドメイン側に置きます。**実体は知りません。**

```rust
pub trait EventStore {
    /// 起きたことを追記する。
    fn append(
        &self,
        user: &User,
        list_name: &ListName,
        events: &[ToDoListEvent],
    ) -> Result<(), ZettaiError>;

    /// 起きたことを順に読み出す。
    fn events_of(
        &self,
        user: &User,
        list_name: &ListName,
    ) -> Result<Vec<ToDoListEvent>, ZettaiError>;
}
```

**更新も削除もありません。** 起きたことは変わらないからです。

読むたびに畳み込みます。**第 5 章で書いた `replay` がそのまま使えます。**

## PostgreSQL と結合テスト

DB を使うテストを、どこで走らせるかを決めました。**測ってからです。**

| 測ったもの | 結果 |
| :--- | :--- |
| CI のサービスコンテナ（同じワークフローで並走させて実測） | **DB 無し 3 秒 / DB 有り 25 秒** |
| `postgres` クレートの依存 | 7 → **67 クレート** |
| 依存の初回ビルド | **+95.47 秒**（一度きり） |
| `just check` 定常 | **足す前と変わらず** |
| DB を使うテストの実行 | **0.37〜0.43 秒** |

**走らせるコストはほとんどありません。** 重いのは 2 つで、**CI のサービスコンテナ（+22 秒）**と、**ローカルで docker が要る**ことです。

Kotlin 版は [ADR-009](../../../adr/ADR-009-integration-test-database.md) で「結合テストを `check` に含める。別ジョブに分けない」と決めました。**Rust 版では分けました。**

理由は数字です。Kotlin 版の `check` は 95 秒で、22 秒足しても割合が変わりません。**Rust 版の `check` は 26 秒で、48 秒になると倍近くなります。** そして別ジョブなら並走するので、**総時間は増えません**。

```just
# 保管との往復。**docker が要る**（`docker compose up -d zettai-db`）。
test-db:
    {{run}} cargo test -p zettai-step4-http --features db --test persistence
```

書いている間は docker 無しで `just check` が回ります。**忘れないように `check-all` には入れてあります。**

### つまずき 1: 並列に走るテストが同じ場所に書く

なでしこ3 版 Unit 7 で踏んだ問題です。記録先を 1 つに決め打ちして、**並列に走るテストが同じファイルに書き、件数が混ざりました。**

Rust では `cargo test` が既定で並列です。**先に手を決めました。**

```rust
fn unique_table(label: &str) -> String {
    static COUNTER: AtomicUsize = AtomicUsize::new(0);
    let n = COUNTER.fetch_add(1, Ordering::SeqCst);
    format!("zettai_events_{label}_{}_{n}", std::process::id())
}
```

スパイクでは**スキーマごと**分けました。テーブル名を変えるだけでは `CREATE TABLE` が競るためです。本番のコードでは 1 つのテーブルを共有しつつ、**テスト用にテーブル名を引数で受ける**形にしています。

## Rust でデータベースに接続する

```rust
        let mut tx = client.transaction().map_err(|e| unavailable(&e))?;
```

**同期です。`async` はありません。**

第 1 章のスパイクで確かめたとおり、`INSERT` も `SELECT` もトランザクションも同期のまま書けました。**`async fn` が第 1〜8 章に伝播することはありませんでした。**

[ADR-024](../../../adr/ADR-024-no-async.md) が持ちこたえた、と言えます。**ただし「持ちこたえた」の中身は「困らなかった」であって、「同期のほうが良かった」ではありません。** 1 プロセスで 1 リクエストずつ扱う連載の規模だから困らなかっただけです。

### つまずき 2: `postgres::Error` は中身を見せない

失敗を包もうとして、最初はこう書きました。

<!-- code-check: ignore つまずきの説明のために、最初に書いた形を引いている -->

```rust
impl From<postgres::Error> for StoreError {
    fn from(e: postgres::Error) -> Self {
        StoreError::QueryFailed { detail: e.to_string() }
    }
}
```

出てきたのはこれです。

```text
詳細: db error
```

**`Display` が `"db error"` としか言いません。** 本当の理由は `source()` の先にあります。たどる関数を書きました。

```text
db error: ERROR: relation "table_that_does_not_exist" does not exist
```

これは持ち込みへの答えにもなりました。第 7 章で「`Display` を自前で書くかは、要るときに決める」としていました（[ADR-031](../../../adr/ADR-031-own-error-enum.md)）。**要りませんでした。** 包む側が `source()` をたどるので、**包まれる側の `Display` に頼らない形**になったからです。

## データベースを準備する

テーブルは 1 つです。

```sql
CREATE TABLE IF NOT EXISTS {table} (
    id BIGSERIAL PRIMARY KEY,
    user_name TEXT NOT NULL,
    list_name TEXT NOT NULL,
    payload JSONB NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
)
```

`payload` は `jsonb` です。**JSON の組み立てを既製品に任せるかを、また測りました。**

| | 自前 | `serde_json` | `serde`(derive) + `serde_json` |
| :--- | :--- | :--- | :--- |
| 依存クレート | **+0** | +5 | +11 |
| ビルド | **0 秒** | 8.30 秒 | 16.57 秒 |

**自前にしました**（[ADR-012](../../../adr/ADR-012-own-json-converter.md) を踏襲）。イベントは 2 種類で、各 1 フィールドです。

**そして、その判断の代償をすぐに払いました。**

### つまずき 3: `jsonb` は入れたままの形では返らない

結合テストが落ちました。

```text
足せる: StoreUnavailable { detail: "読めない記録: {\"type\": \"ListCreated\", \"value\": \"book\"}" }
```

**コロンの後ろに空白が入っています。** `jsonb` は受け取った JSON を正規化して保存し、正規化した形で返します。自前のパーサは `"type":"` を探していたので、読めませんでした。

**単体の往復テストは通っていました。** 自分で書いた文字列を自分で読み返していたからです。**DB を通す結合テストだけが見つけました。**

直した形がこれです。

```rust
fn field(text: &str, key: &str) -> Option<String> {
    let needle = format!("\"{key}\"");
    let start = text.find(&needle)? + needle.len();
    let rest = text[start..].trim_start();
    let rest = rest.strip_prefix(':')?.trim_start();
    let rest = rest.strip_prefix('"')?;
```

**これは「自前で書く」判断の代償です。** `serde_json` なら起きませんでした。依存 +5・ビルド +8.30 秒と引き換えに、**この欠陥を自分で見つけて直す手間**を払っています。

正直に書くと、**判断の是非はこの 1 件では決まりません**。第 12 章でログの JSON が要るとき、もう一度測ります。そのとき「自前のパーサが 3 か所に増えている」なら、判断は変わります。

## 関数型スタイルでリモートデータにアクセスする

コマンドの処理は、**読む・決める・書く**の 3 つが続きます。どれも失敗しえます。

```rust
pub fn handle_with_store(
    store: &dyn EventStore,
    user: &User,
    list_name: &ListName,
    command: crate::ToDoListCommand,
) -> Result<crate::ToDoList, ZettaiError> {
    let before = store.events_of(user, list_name)?;
    let new_events = crate::execute(user, list_name, &before, command)?;
    store.append(user, list_name, &new_events)?;

    let all = [before, new_events].concat();
    Ok(crate::replay(all))
}
```

**`?` が 3 回続いています。** どれが失敗しても、理由がそのまま呼び手に返ります。

**断られたら書きません。** `execute` が `Err` を返した時点で `append` に行きません。これはコードの構造がそう言っているだけで、条件分岐を書いていません。

## モナドの力を探る

`?` が短く書けるのは、`Result` がモナドだからです。**確かめます。**

```rust
#[test]
fn associativity() {
    let mut rng = Rng::new(27182818);
    for _ in 0..TRIALS {
        let outcome = any_outcome(&mut rng);

        let left = outcome.clone().and_then(add_one).and_then(rename);
        let right = outcome.and_then(|list| add_one(list).and_then(rename));

        assert_eq!(left, right);
    }
}
```

**`and_then` は言語にあります。** Kotlin 版は `Outcome.bind` を、なでしこ3 版は `結果連鎖` を書きました。ここで書くのは法則の確認だけです。

これで代数構造が 3 つ揃いました。

| 構造 | 章 | Rust では |
| :--- | :--- | :--- |
| モノイド | 5 | 自前（`identity` / `compose`） |
| ファンクタ | 7・8 | **`Result::map` は言語。射影の `map` は包み直すだけ** |
| モナド | 9 | **`Result::and_then` は言語** |

### つまずき 4: clippy が法則のテストを 2 度止めた

右恒等則（`m.and_then(Ok)` は `m` と同じ）を書いたら止まりました。

```text
error: using `Result.and_then(Ok)`, which is a no-op
```

**no-op であること自体が確かめたい法則です。** 第 7 章の `map_identity` と同じ形で、これで 2 件目です。理由を書いて許可しました。

**言語が構造を与えると、その構造を確かめるテストが「無駄なコード」に見える**ようです。

### 検出率

```text
隠れた欠陥の検出 47 / 200    （期待値 50・σ 6.12）
```

壊し方は「項目が 1 件のときだけ続きを飛ばす」です。実測 47 は期待値から 0.5σ 以内でした。

第 8 章では 1.8σ 上に出て、生成器を疑って数え直しました。**σ のぶんの余裕は実際に要ります。**

## 読み戻す

つなぎ直して読めることを確かめます。**プロセスの中の状態に頼っていない**ことの確認です。

```rust
    // **つなぎ直す。** プロセスの中の状態に頼っていないことを確かめる。
    let reopened = PostgresEventStore::connect(CONN, &table).expect("つなぎ直せる");
    let list = load(&reopened, &uberto(), &book()).expect("読み戻せる");
```

## 3 経路目

受け入れシナリオに、**保管を通す経路**を足しました（[ADR-020](../../../adr/ADR-020-third-route.md)）。

第 3 章で作ったトレイトに、3 つめの実装を足すだけです。**シナリオは 1 行も変えていません。**

| 経路 | 通るもの |
| :--- | :--- |
| ドメイン直接 | ハブと畳み込み |
| HTTP 経由 | 上 + パスの分解と HTML |
| **保管経由** | 上 + **JSON の往復と SQL** |

なでしこ3 版では 3 経路目が**経路固有の欠陥**を捕まえました。今回捕まえたのは `jsonb` の正規化で、**これも保管経由でしか出ない欠陥**です。

## まとめ

- **起きたことだけを保存しました。** 状態は保存せず、読むたびに畳み込みます
- **`async` を採らなかった判断は持ちこたえました。** ただし中身は「困らなかった」で、「同期のほうが良かった」ではありません
- **DB の検査を別ジョブに分けました。** Kotlin 版とは違う判断で、理由は `check` が 26 秒だからです。**同じ ADR の題材でも、数字が違えば結論が変わります**
- **`postgres::Error` は `Display` で中身を見せません。** `source()` をたどります。**おかげで自前の `Display` が要らなくなりました**
- **自前の JSON が `jsonb` の正規化で落ちました。** 単体の往復テストは通っていて、**DB を通す結合テストだけが見つけました**。これは「自前で書く」判断の代償です
- **`?` が 3 回続きます。** 断られたら書かない、を条件分岐なしで書けます
- **代数構造が 3 つ揃いました。** モノイドは自前、ファンクタとモナドは言語にありました
- **clippy が法則のテストを 2 度止めました。** 言語が構造を与えると、確かめるテストが無駄に見えるようです
- **3 経路目が `jsonb` の欠陥を捕まえました。** 経路を増やす理由がまた 1 つ増えました

Phase 2 はここまでです。次の章から、境界とライフタイムに入ります。

---

## この章で書いたコード

- ポート: `apps/rust/zettai/zettai-step4-domain/src/store.rs`
- 実装: `apps/rust/zettai/zettai-step4-http/src/event_store.rs`、`apps/rust/zettai/zettai-step4-http/src/acceptance.rs`
- 性質テスト: `apps/rust/zettai/zettai-step4-domain/tests/monad_laws.rs`
- 結合テスト: `apps/rust/zettai/zettai-step4-http/tests/persistence.rs`
- 検査: `apps/rust/zettai/justfile`、`.github/workflows/rust-zettai.yml`
- スパイク: `apps/rust/zettai/spikes/sync-postgres/`、`apps/rust/zettai/spikes/error-crates/`
- 制約の一覧: `apps/rust/zettai/README.md`

本文のコードは、上のファイルからの転記です。次の 3 種類だけは逐語の転記ではありません。

- コマンドの実行例と、その出力
- つまずきの説明のために、わざと壊したコード
- 比較のために引いた他言語のコード

## 参照

Uberto Barbini『From Objects to Functions』第 9 章。題材と章構成をこの本に拠っています。本連載のコードはすべて書き起こした自作実装で、原著のコードを転載したものではありません。

- [Zettai — Rust 版](index.md) / [第 8 章](chapter08.md) / [Kotlin 版の第 9 章](../kotlin/chapter09.md) / [なでしこ3 版の第 9 章](../nadesiko/chapter09.md)
- [ADR-008 イベントストア](../../../adr/ADR-008-event-store-single-table.md) / [ADR-009 結合テストの DB](../../../adr/ADR-009-integration-test-database.md) / [ADR-012 JSON の変換](../../../adr/ADR-012-own-json-converter.md) / [ADR-020 3 経路目](../../../adr/ADR-020-third-route.md) / [ADR-024 async を採らない](../../../adr/ADR-024-no-async.md) / [ADR-031 失敗の enum](../../../adr/ADR-031-own-error-enum.md)
