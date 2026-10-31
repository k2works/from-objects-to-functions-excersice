---
type: Retrospective
title: "Unit 4 のふりかえり（KPT）- Zettai 連載（Kotlin FP 版）"
description: "Kotlin FP 版 Unit 4 の command ADT、状態遷移、Arrow typed error、第 6〜7 章についての KPT ふりかえり。"
tags: [development, retrospective, zettai, kotlin, arrow, unit-4]
status: draft
generated: { by: anthropic/claude-opus-5-5, at: 2026-10-31T06:10:00Z }
---

# Unit 4 のふりかえり（KPT）- Zettai 連載（Kotlin FP 版）

## Keep

- 4 × 4 の遷移表を手書きの期待値として独立させ、16 マスと許可 5 件を実行可能な仕様にできました。
- `handle(command, state)` を純粋関数に保ち、Arrow は `ensure` / `ensureNotNull` / `bind` による失敗の短絡だけに使いました。domain rule は遷移表と条件式に残っています。
- sealed error と else のない `when` により、error を追加すると HTTP の写像漏れが compile error になる形を作れました。
- Unit 3 の Try（adapter の aliasing test、driving / driven の依存矢印、fact check）を計画と実装に反映できました。
- 5 観点レビューと独立した再レビューで、記事の事実誤認（Raise の説明、波及ファイル表の内訳）を公開前に見つけられました。

## Problem

- error response に Content-Type がなく、成功側だけ escape している非対称をレビューまで見落としました。
- 受入条件を片側だけで検証していました。拒否時に persist しないテストは `ListNotFound` だけで、HTTP の写像は 4 種中 2 種でした。
- 計画にあった handler の aliasing test と、driving / driven adapter の境界テストが実装されていませんでした。
- 記事に計画用語の「Unit 4/5/6」が残り、既存 Kotlin 版との選択差（成功条件）の説明が不足していました。
- 実装を commit しないまま執筆まで進めたため、初回実装で Red を確認した記録が履歴に残りませんでした。
- `gulp mkdocs:build` は `--strict` なしでビルドするため、CI と同じ判定にするには Docker で直接 `mkdocs build --strict` を実行する必要がありました。

## Try

- Unit 5 では、受入条件ごとに成功側と拒否側のテストを対にし、error type の数だけ表駆動で並べます。
- 計画のタスクに書いたテストは、DoD の確認時にテスト名と 1 対 1 で突き合わせます。
- 実装の Green ごとに commit し、Red → Green の履歴を残してから記事を書きます。
- 記事は「計画用語を使わない」「各章で既存 Kotlin 版との選択差を書く」をレビュー前の自己点検項目にします。
- Unit 5 の着手前に、Kotlin FP 版の ADR（Arrow 採用、関数型 DI、suspend port、transaction 境界）を作ります。
- `gulp mkdocs:build` を CI と同じ strict 判定にするかを運用タスクとして検討します。

## 関連ドキュメント

- [Unit 4 の Bolt 計画](iteration_plan-kotlin-fp-4.md)
- [Bolt 終了報告](bolt_report-kotlin-fp-4.md)
- [開発成果物レビュー](../review/kotlin-fp-unit4_review_20261031.md)
