---
type: Retrospective
title: "Unit 2 のふりかえり（KPT）- Zettai 連載（Kotlin FP 版）"
description: "Kotlin FP 版 Unit 2 の HTTP ウォーキングスケルトン、Cucumber 2 経路、第 2〜3 章についての KPT ふりかえり。"
tags: [development, retrospective, zettai, kotlin, unit-2]
status: draft
generated: { by: openai-codex/gpt-5, at: 2026-10-30T05:24:30Z }
---

# Unit 2 のふりかえり（KPT）- Zettai 連載（Kotlin FP 版）

## Keep

- HTTP の外側から Red を作り、最小の domain / application / web を導出できました。
- 同じ業務 scenario を domain / HTTP の双方へ適用し、経路名を feature から除けました。
- 記事コード検査、境界テスト、5 観点レビューが設計と説明の不一致を検出しました。

## Problem

- 最初の `ToDoListFetcher` を `web` に置き、domain 経路を外側へ依存させました。
- 「高階関数を使う」節を最初は interface で実装し、Kotlin FP 版の主題と一致しませんでした。
- missing scenario の挿入位置を誤り、feature の scenario 境界を一度壊しました。
- Kotlin incremental cache の欠落 class で一度 `check` が失敗しました。

## Try

- Unit 3 では実装前に package 間の依存矢印を Bolt 計画へ 1 行で書きます。
- 節タイトルが要求する概念を、記事だけでなくコンパイルされるコードに表します。
- feature 変更後は対象 Cucumber test を再実行してから全体 `check` へ進みます。
- 品質ゲートの最後は incremental build だけでなく `clean check` も実行します。

## 関連ドキュメント

- [Unit 2 の Bolt 計画](iteration_plan-kotlin-fp-2.md)
- [Bolt 終了報告](bolt_report-kotlin-fp-2.md)
- [開発成果物レビュー](../review/kotlin-fp-unit2_review_20261030.md)
