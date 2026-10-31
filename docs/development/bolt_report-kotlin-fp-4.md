---
type: Bolt Report
title: "Unit 4 の Bolt 終了報告 - Zettai 連載（Kotlin FP 版）"
description: "Kotlin FP 版 Unit 4 の command ADT、状態遷移、Arrow typed error、第 6〜7 章の完了結果を記録する。"
tags: [development, report, zettai, kotlin, arrow, unit-4]
status: draft
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-31T06:10:00Z }
---

# Unit 4 の Bolt 終了報告 - Zettai 連載（Kotlin FP 版）

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Unit | 4：command と typed error |
| Bolt | 4-1 第 6 章 / 4-2 第 7 章 |
| 実施日 | 2026-10-31 |
| 見積もり | US-006: 5 SP / US-007: 5 SP |
| 状態 | **完了** |

## 成果

- `CreateToDoList`、`AddToDoItem`、`ChangeItemStatus` を sealed command とし、`ItemStatusChanged` を immutable event として追加しました。
- 4 × 4 の遷移表を値として定義し、16 マスすべてと許可 5 件をテストで固定しました。
- `handle(command, state)` を純粋な state machine とし、拒否理由を 4 種の sealed `ZettaiError` で返しました。
- Hub は Raise DSL の `bind()` で失敗を短絡させ、検証に通った event だけを 1 回永続化します。
- HTTP adapter は `Either` を `fold` し、error を 404 / 400 の plain text response に網羅的に写像します。
- `Either.map` の恒等則と合成則を、4 種の error と生成した関数を使う Kotest property testing で検証しました。
- 第 6〜7 章、設計文書、索引、nav を実装と同期し、既存 Kotlin 版の `Outcome` との対応と選択差を記事に記録しました。

## 品質結果

| ゲート | 結果 |
| :--- | :--- |
| `gradlew.bat clean check --no-daemon` | 成功 |
| Unit 4 の Kotest | 30 件成功（レビュー前 21 件） |
| Unit 1〜3 の回帰 | 成功。Testcontainers PostgreSQL と Unit 2 の Cucumber を含む |
| 記事検査（コード・コード形式・節構成・数値・引用・ADR 参照） | 違反 0 |
| OKF | ERROR 0、既存 WARN 10 |
| `mkdocs build --strict` | 成功（Docker 上で実行） |
| 5 観点レビュー | 統合後 高 6 / 中 9 / 低 12。修正後の高・中残件 0（独立した再レビューで確認） |
| CI | 未 push のため未実行。ローカル同等ゲートは成功 |

カバレッジ計測は本 project で未設定です。Unit 2 の画面を変更していないため、UI / manual の更新は対象外です。

## Unit 5 への引き継ぎ

- `StateFetcher` が全状態を replay する形を、集約単位で読む port に絞ります。
- transaction 境界と `persist` の typed error（競合の表現を含む）を ADR で決めます。
- read model の projection と整合性モデルを決めます。
- `runBlocking` と JDBC の blocking I/O の扱いを ADR で決めます。
- Kotlin FP 版の ADR（Arrow 採用、関数型 DI、suspend port）を着手前に作ります。
- 不正な event を replay が黙って捨てる挙動の検出方針を決めます。

## 関連ドキュメント

- [Unit 4 の Bolt 計画](iteration_plan-kotlin-fp-4.md)
- [Unit 4 のふりかえり](retrospective-kotlin-fp-4.md)
- [第 6 章](../article/zettai/kotlin-fp/chapter06.md)
- [第 7 章](../article/zettai/kotlin-fp/chapter07.md)
- [開発成果物レビュー](../review/kotlin-fp-unit4_review_20261031.md)
