# ADR (Architecture Decision Records)

技術的意思決定を記録した ADR です。

## ADR 一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [ADR-001](ADR-001-kotlin-toolchain.md) | サンプル実装に Kotlin 2.2 / JDK 21 を採用し、ビルドの正を Gradle 側に置く | 提案 |
| [ADR-002](ADR-002-ddt-pesticide.md) | 受け入れテストを Cucumber ではなく DDT / Pesticide で書く | 提案 |
| [ADR-003](ADR-003-http4k-6.md) | http4k 6.x を採用し、原著の 4.x との差分は章ごとに注記する | 提案 |
| [ADR-004](ADR-004-static-analysis.md) | 静的解析を `check` に組み込まず、記事のコード例検査を優先する | 提案 |
| [ADR-005](ADR-005-property-based-testing.md) | プロパティベーステストを自前で書き kotest-property を導入しない | 提案 |

ADR の作成には `creating-adr` スキルを使用してください。
