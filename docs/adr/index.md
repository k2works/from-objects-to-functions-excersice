# ADR (Architecture Decision Records)

技術的意思決定を記録した ADR です。ADR-001〜012 は Kotlin 版、ADR-013 以降はなでしこ3 版の判断です。

## ADR 一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [ADR-001](ADR-001-kotlin-toolchain.md) | サンプル実装に Kotlin 2.2 / JDK 21 を採用し、ビルドの正を Gradle 側に置く | 提案 |
| [ADR-002](ADR-002-ddt-pesticide.md) | 受け入れテストを Cucumber ではなく DDT / Pesticide で書く | 提案 |
| [ADR-003](ADR-003-http4k-6.md) | http4k 6.x を採用し、原著の 4.x との差分は章ごとに注記する | 提案 |
| [ADR-004](ADR-004-static-analysis.md) | 静的解析を `check` に組み込まず、記事のコード例検査を優先する | 提案 |
| [ADR-005](ADR-005-property-based-testing.md) | プロパティベーステストを自前で書き kotest-property を導入しない | 提案 |
| [ADR-006](ADR-006-outcome-port-type.md) | 失敗を `Outcome` で表しポートの型を変える | 提案 |
| [ADR-007](ADR-007-module-per-unit.md) | モジュールを Unit の境界で切る | 提案 |
| [ADR-008](ADR-008-event-store-single-table.md) | イベントストアを 1 テーブルで持ち状態を保存しない | 提案 |
| [ADR-009](ADR-009-integration-test-database.md) | 結合テストの DB を docker-compose と CI のサービスコンテナで用意する | 提案 |
| [ADR-010](ADR-010-own-template.md) | テンプレート機構を自前で書き既製のテンプレートエンジンを使わない | 提案 |
| [ADR-011](ADR-011-transaction-boundary.md) | トランザクションの境界をコマンド 1 つの処理に置き文脈を不透明な型にする | 提案 |
| [ADR-012](ADR-012-own-json-converter.md) | JSON の変換を自前の `Converter` で書き Kondor を導入しない | 提案 |
| [ADR-013](ADR-013-cnako3-runtime.md) | なでしこ3 版の処理系に cnako3 を採用する | 提案 |
| [ADR-014](ADR-014-own-test-framework.md) | なでしこ3 版のテスト基盤を自作し、静的解析は文法検査だけにする | 提案 |
| [ADR-015](ADR-015-step-directories.md) | 章の段階ごとにディレクトリを切り、過去の章のコードを残す | 提案 |
| [ADR-016](ADR-016-own-acceptance-entry.md) | 受け入れテストの入口を自作し、ライブラリに寄せない | 提案 |
| [ADR-017](ADR-017-own-property-testing.md) | 性質テストを自作し、検出率を測ってから信用する | 提案 |
| [ADR-018](ADR-018-outcome-dict-port.md) | 失敗を結果辞書で表し、ポートの契約を変える | 提案 |
| [ADR-019](ADR-019-file-event-log.md) | イベントログを 1 ファイルに追記し、状態を保存しない | 提案 |
| [ADR-020](ADR-020-third-route.md) | 受け入れシナリオに永続化を通す 3 経路目を足す | 提案 |
| [ADR-021](ADR-021-context-as-path.md) | 文脈を保存先の指し先として表し、組み立てない | 提案 |
| [ADR-022](ADR-022-template-with-unfilled-check.md) | テンプレートを自前で書き、埋めそこねを実行時に止める | 提案 |
| [ADR-023](ADR-023-builtin-json-with-converter.md) | 組み込みの JSON を使い、その上に双方向変換を置く | 提案 |

ADR の作成には `creating-adr` スキルを使用してください。
