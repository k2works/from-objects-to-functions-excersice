---
type: Retrospective
title: "Unit 1 のふりかえり（KPT）- Zettai 連載（Kotlin FP 版）"
description: "Kotlin FP 版 Unit 1 の JDK 25 基盤、Cucumber、Testcontainers、第 1 章についての KPT ふりかえり。"
tags: [development, retrospective, zettai, kotlin, unit-1]
status: stable
generated: { by: openai-codex/gpt-5, at: 2026-10-30T05:24:30Z }
---

# Unit 1 のふりかえり（KPT）- Zettai 連載（Kotlin FP 版）

## Keep

- JDK 25、Gradle、Kotlin、Arrow、Cucumber、Testcontainers を最初の Unit で実物検証できました。
- pure / `Either` / `suspend` / HTTP boundary を小さなテストで分け、effect の置き場所を明示できました。
- 記事、サンプルコード、CI、MkDocs を同じ Unit で同期できました。

## Problem

- Cucumber の 2 経路は基盤確認用の得点計算であり、Zettai の業務シナリオではありませんでした。
- Unit 完了時点でふりかえりと Bolt 終了報告が未作成でした。
- Kotlin FP の進捗表に第 1 章の公開状態が残っていました。

## Try

- Unit 2 では実際の ToDo リスト閲覧を domain / HTTP の 2 経路で通します。
- 実装、記事、索引、報告書を Unit の品質ゲートでまとめて確認します。
- test adapter の経路選択をプロダクションコードへ入れません。
