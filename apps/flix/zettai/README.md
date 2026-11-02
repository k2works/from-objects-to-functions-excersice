# Zettai（Flix 版）

Zettai 連載 4 言語目のサンプル実装です。記事は [`docs/article/zettai/flix/`](../../../docs/article/zettai/flix/) にあります。

## 実行環境

```bash
nix develop .#flix
cd apps/flix/zettai
flix test
```

| 項目 | バージョン |
| :--- | :--- |
| Flix | 0.79.0（`flix.toml` で固定） |
| JDK | 21（Flix の要件は 21 以降） |

`flix` は devShell が提供する実行可能なスクリプトです。初回に公式リリースの `flix.jar` を `~/.cache/flix/` に取得します（`FLIX_JAR` で保存先を変えられます）。`nix develop --command flix test` のような非対話の使い方もできます。Nix を使わない場合は、`flix.jar` を取得して `java -jar flix.jar` で同じコマンドを実行します。

## データベース（第 9 章から）

`flix test` は PostgreSQL に接続する結合テストを含みます。**Docker が要ります。**

```bash
docker compose up -d zettai-db
```

接続先は環境変数 `ZETTAI_DB_URL` で変えられます。既定は `jdbc:postgresql://localhost:5432/zettai`（ユーザー・パスワードとも `zettai`）です。5432 番が他のコンテナで使われている場合は、別のポートで PostgreSQL を起動し、次のように指定します。

```bash
ZETTAI_DB_URL=jdbc:postgresql://localhost:55433/zettai flix test
```

JDBC ドライバ（`org.postgresql:postgresql`）は `flix.toml` の `mvn-dependencies` に書いてあり、初回の `flix test` で Maven から取得されます。

## モジュールの構成

Scala 版・Kotlin FP 版の層に合わせて、モジュール名とディレクトリを層ごとの階層にしています。

| モジュール | 中身 |
| :--- | :--- |
| `Foundation` | 第 1 章の最小の例 |
| `Domain` | `User`、`ListName`、`ToDoItem`、`ToDoList` と規則 |
| `Domain.Events`（`Replay`、`Transition`） | イベント、状態、畳み込み、状態変換 |
| `Domain.Commands` | コマンドと `handle` |
| `Domain.Errors` | `ZettaiError` |
| `Domain.Queries.Projection` | 射影 |
| `Domain.Validation.Validate` | `Validation` による検証 |
| `Application`（`Hub`、`CommandHub`、`QueryHub`、`EventStore`、`Process`、`Lookup`） | 入口と文脈 |
| `Web`（`Http`、`Html`、`JdkServer`） | HTTP の殻 |
| `Adapter.Postgres`（`PostgresStore`、`Codec`） / `Adapter.Logging` | 外との接続 |
| `Json`（`Converter`、`Profunctor`） / `Logging.Logs` | JSON とログの記録 |

Flix の規則で、入れ子のモジュールには次が要ります。

- 他のモジュールから使うモジュールは `pub mod` で宣言する
- 親のモジュールも宣言する（`Application`、`Adapter`、`Adapter.Postgres`、`Web`、`Logging`、`Domain.Queries`、`Domain.Validation` は、空の宣言のファイル）
- モジュール `A.B` は、ファイル `A/B.flix` に置く（ディレクトリ名は大文字始まり）

テスト（`test/`）は、モジュール名もディレクトリも分けていません。

## コマンド

```bash
flix check   # 型と効果の検査
flix test    # テスト
```

## 既知の制約

Unit 1 時点で確認できたことだけを書きます。章を進めるたびに追記します。

| 制約 | 内容 |
| :--- | :--- |
| 版の固定 | Flix は変更が速いため `flix.toml` の `flix` で固定する。上げるときは記事のコードと出力も取り直す |
| Windows のコンソール | 既定のコードページ（日本語環境では Shift_JIS 系）で、エラー出力の日本語が文字化けする。ソースは UTF-8 で、テストの判定には影響しない |
| Nix | Docker 上の Nix（Linux）で確かめた。Windows / macOS の Nix は確かめていない |
| DriverManager | `DriverManager.getConnection` は「No suitable driver」になる。ドライバを `new Driver().connect(...)` で直接生成している |
| テストの後始末 | 結合テストはテストごとに専用のテーブルを作り、終了時に捨てる。**テストが途中で失敗すると、テーブルが残る**（`events_` で始まる名前） |
