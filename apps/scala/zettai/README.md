# Zettai（Scala 版）

Zettai 連載 5 言語目（6 実装方針目）のサンプル実装です。記事は [`docs/article/zettai/scala/`](../../../docs/article/zettai/scala/) にあります。Scala 3 と Cats Effect で書きます。

## 実行環境

```bash
nix develop .#scala
cd apps/scala/zettai
sbt test
```

| 項目 | バージョン |
| :--- | :--- |
| Scala | 3.3.9（`build.sbt` で固定。3.3 系の LTS） |
| sbt | 1.10.11（`project/build.properties` で固定） |
| JDK | 21（devShell が提供） |
| Cats | 2.13.0 |
| Cats Effect | 3.7.1 |
| MUnit / munit-cats-effect | 1.3.6 / 2.2.1（テスト） |
| doobie | 1.0.0-RC13（RC 版。安定版の 1.0 は未公開） |
| Testcontainers | 2.0.5（テスト。Docker が要る） |

Nix を使わない場合は、JDK 21 以降と sbt を自分で用意して同じコマンドを実行します。初回は依存ライブラリの取得で時間がかかります。

## パッケージ構成

Kotlin FP 版（`apps/kotlin-fp/zettai/`）の層に合わせて、`zettai` の下を層ごとのサブパッケージに分けています。

| パッケージ | 中身 |
| :--- | :--- |
| `zettai` | 第 1 章の `Foundation` |
| `zettai.domain` | `User`、`ListName`、`ToDoItem`、`ToDoList` と規則 |
| `zettai.domain.events` | イベント、状態、`Replay`、`StateTransition` |
| `zettai.domain.commands` | コマンドと `handle` |
| `zettai.domain.errors` | `ZettaiError` |
| `zettai.domain.queries` | 射影（`Projection`） |
| `zettai.domain.validation` | `Validated` による検証 |
| `zettai.application` | `Hub`、`CommandHub`、`QueryHub`、`EventStore`、`Process` |
| `zettai.web` | `ZettaiRoutes`、`Html`、`Server` |
| `zettai.adapter.postgres` | `PostgresStore`（doobie） |
| `zettai.adapter.logging` | `Logging`、`LogSink` |
| `zettai.json` / `zettai.logging` | `Json`、`Converter`、`Dimap` / `LogRecord` |
| `zettai.app` | `Main` |

Kotlin FP 版との違い: インメモリの実装は `adapter.inmemory` に分けず、`EventStore` と `Hub` のコンパニオンに置いています。テストは、対象と同じパッケージに置き、受け入れテストの部品は `zettai.acceptance` にあります。

## コマンド

```bash
sbt test      # テストの実行（コンパイルを含む）
sbt compile   # 本体のコンパイル
sbt "Test/runMain io.cucumber.core.cli.Main --glue zettai.acceptance.steps classpath:features"   # Cucumber（確認用）
```

## データベース（第 9 章から）

結合テストは、PostgreSQL（`postgres:17-alpine`）をテスト自身が Testcontainers で起動します。**Docker が動いている必要があります。** 接続先を環境変数で指定する必要はありません。

## 既知の制約

- doobie は 1.0.0-RC13。RC 版のため、安定版が出たときにパッケージ名や API の変更を取り込む必要がありうる
- 接続プールは使っていない（トランザクションごとに接続を開く）

- Windows の日本語環境では、sbt の出力に含まれる日本語が文字化けする。ソースは UTF-8 で、テストの判定には影響しない
- ライブラリの版は組み合わせを確かめて固定した。上げるときは全テストを通し直す
