---
type: Retrospective
title: "Unit 3 のふりかえり（KPT）- Zettai 連載（Kotlin FP 版）"
description: "Kotlin FP 版 Unit 3 の関数型 DI、イベント畳み込み、状態変換の法則、第 4〜5 章についての KPT ふりかえり。"
tags: [development, retrospective, zettai, kotlin, unit-3]
status: draft
generated: { by: openai-codex/gpt-5, at: 2026-10-31T00:15:57Z }
---

# Unit 3 のふりかえり（KPT）- Zettai 連載（Kotlin FP 版）

## Keep

- 実装前に package の依存矢印を書き、domain、application、driven adapter、driving adapter の責務を分離できました。
- Red から関数型 port、constructor function、イベント replay を導出し、固定例と property test の両方で検証できました。
- defensive copy と別名参照のテストにより、Kotlin の read-only collection と immutable state の違いを実装へ反映できました。
- Unit 1 の Testcontainers、Unit 2 の Cucumber を含む `clean check` を回帰ゲートとして維持できました。

## Problem

- 最初は HTTP renderer に期日と状態を表示し、Unit 2 の画面を変更しないという計画と不一致になりました。
- in-memory fetcher を application package に置き、port と driven adapter の区別が曖昧でした。
- `Map` を read-only 型で公開するだけでは、入力側の可変参照から状態が変わり得る点を初回実装で見落としました。
- 記事進捗の重複値と原著者名の誤りがあり、テクニカルライターレビューで修正しました。
- Unit 2 の Cucumber を test class filter 付きで再実行した際、incremental cache による `IncompatibleClassChangeError` が発生し、module の clean test が必要になりました。

## Try

- Unit 4 では read-only と immutable を設計時に区別し、可変参照を渡す失敗テストを先に書きます。
- driving adapter と driven adapter の依存矢印を Bolt 計画で別々に示します。
- 記事更新時に章数、固有名詞、参照先を fact check の対象に含めます。
- Cucumber 回帰は test class filter に依存せず、対象 module の `clean test` から実行します。

## 関連ドキュメント

- [Unit 3 の Bolt 計画](iteration_plan-kotlin-fp-3.md)
- [Bolt 終了報告](bolt_report-kotlin-fp-3.md)
- [開発成果物レビュー](../review/kotlin-fp-unit3_review_20261031.md)
