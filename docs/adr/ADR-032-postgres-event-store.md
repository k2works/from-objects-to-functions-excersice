---
type: ADR
title: "ADR-032 イベントを 1 テーブルに追記し、外のエラーを境界で包む"
description: "Zettai 連載 Rust 版の永続化の決定。ADR-008 を踏襲して 1 テーブルに追記し状態を保存しないこと、postgres のエラーを境界で自前の enum に包むこと、JSON を自前で書いた判断とその代償を記録する。"
tags: [adr, rust, zettai]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-29T08:10:20Z }
---

# ADR-032 イベントを 1 テーブルに追記し、外のエラーを境界で包む

日付: 2026-09-29

## ステータス

2026-09-29 提案されました

## コンテキスト

第 9 章で永続化します。3 対象で共通の主題ですが、保存先が違います。

| 対象 | 保存先 |
| :--- | :--- |
| Kotlin | PostgreSQL（[ADR-008](ADR-008-event-store-single-table.md)） |
| なでしこ3 | 追記型のファイル（[ADR-019](ADR-019-file-event-log.md)。DB プラグインが無い） |
| **Rust** | **PostgreSQL**（Unit 1 のスパイクで同期クレートで書けることを確認済み） |

決めることは 3 つです。**何を保存するか・外のエラーをどう扱うか・JSON をどう作るか**です。

## 決定

### 1. イベントを 1 テーブルに追記し、状態を保存しない

[ADR-008](ADR-008-event-store-single-table.md) を踏襲します。

```sql
CREATE TABLE IF NOT EXISTS {table} (
    id BIGSERIAL PRIMARY KEY,
    user_name TEXT NOT NULL,
    list_name TEXT NOT NULL,
    payload JSONB NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
)
```

- 更新も削除もしない
- 読むたびに第 5 章の `replay` で畳み込む
- **1 つのコマンドが生んだイベントは、まとめて入るか入らないか**（トランザクション）

### 2. 外のエラーを境界で包む

`postgres::Error` を**ドメインに持ち込みません**。持つとドメインのクレートが `postgres` に依存し、[ADR-027](ADR-027-crate-boundary.md) が壊れます。

`ZettaiError::StoreUnavailable { detail: String }` に包みます。

**`Display` をたどる必要がありました。** `postgres::Error` の `Display` は `"db error"` としか出さず、本当の理由は `source()` の先にあります。

```text
db error: ERROR: relation "table_that_does_not_exist" does not exist
```

これで [ADR-031](ADR-031-own-error-enum.md) の「`Display` が要るかは、要るときに決める」に答えが出ました。**要りません。** 包む側が `source()` をたどるので、包まれる側の `Display` に頼らない形になったためです。

### 3. JSON を自前で書く

[ADR-012](ADR-012-own-json-converter.md) を踏襲します。測ってから決めました。

| | 自前 | `serde_json` | `serde`(derive) + `serde_json` |
| :--- | :--- | :--- | :--- |
| 依存クレート | **+0** | +5 | +11 |
| ビルド | **0 秒** | 8.30 秒 | 16.57 秒 |

イベントは 2 種類・各 1 フィールドで、30 行に収まります。

**この判断の代償をすぐに払いました。** `jsonb` は受け取った JSON を正規化して返すので、**コロンの後ろに空白が入ります**。自前のパーサは `"type":"` を探していて読めませんでした。

**単体の往復テストは通っていました。** 自分で書いた文字列を自分で読み返していたためです。DB を通す結合テストだけが見つけました。

### 検討した代替案

| 案 | 採らなかった理由 |
| :--- | :--- |
| 状態のテーブルも作る | イベントと状態が二重管理になる。3 対象で共通の判断 |
| `postgres::Error` をドメインに持つ | **ドメインが `postgres` に依存する**（[ADR-027](ADR-027-crate-boundary.md) 違反） |
| `serde_json` を入れる | 依存 +5・ビルド +8.30 秒。**ただしこの判断は 1 件の代償を払った。第 12 章で測り直す** |
| ファイルに保存する（なでしこ3 版） | PostgreSQL で書けることを Unit 1 で確認済み。**Kotlin 版と同題材のほうが比較が効く** |

## 影響

- ドメインのクレートは**依存 0 のまま**です。`postgres` は HTTP 側のクレートにあります
- 失敗の理由が 1 つ増え、状態コードに写す `match` が**また止まりました**（3 回目）。`StoreUnavailable` は 503 にしました
- テーブル名を引数で受けます。**テストごとに分けるため**です（なでしこ3 版 Unit 7 で件数が混ざりました）
- 依存が 7 → 67 クレートに増えました。**初回ビルド +95.47 秒**（一度きり）

### この決定が解かないこと

| 解かないこと | どうするか |
| :--- | :--- |
| ~~JSON を自前で書き続けるか~~ | **[ADR-037](ADR-037-own-logging.md) で閉じました**（2026-09-29）。測り直して自前を続けています。**条件の書き方（「3 か所」の数え方）が曖昧だった**ことも記録しました |
| ~~トランザクションの境界~~ | **[ADR-034](ADR-034-transaction-as-scope.md) で閉じました**（2026-09-29）。区間として貸す形にし、「読む → 判断する → 書く」が 1 つの単位になっています |
| 読み出しの量 | 全イベントを読んで畳み込みます。**件数が増えたときの手（スナップショット）は決めていません**。連載の規模では要りません |
| 接続の使い回し | いまは `Mutex<Client>` 1 本です。**並行して捌く話は連載の範囲外**です |

## コンプライアンス

- ドメインの `Cargo.toml` に `postgres` が現れないこと
- 状態を保存するテーブルが無いこと
- `postgres::Error` がドメインの型に現れないこと
- DB を使うテストがテーブル名を共有しないこと
