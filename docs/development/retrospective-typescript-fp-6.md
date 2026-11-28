---
type: Retrospective
title: "Unit 6 のふりかえり（KPT）- Zettai 連載（TypeScript FP 版）"
description: "TypeScript FP 版 Unit 6 の第 10〜11 章についての KPT ふりかえり。"
tags: [development, retrospective, zettai, typescript, effect, unit-6]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-28T05:48:17Z }
---

# Unit 6 のふりかえり（KPT）- Zettai 連載（TypeScript FP 版）

## Keep

- 守り方を、実際の PostgreSQL で数を取って比べました
- テストが通る壊し方を探しました。**テストの穴が 2 つ見つかりました**
- 生成の割合のテストが、イベントの追加による偏りを検出しました。閾値を下げず、生成を直しました
- フォームを実際のサーバーに送って、守りが無いことを確かめました

## Problem

- Unit 6 の計画を、第 10 章の実装の後に書きました
- 通知に頼る接続のテストは、壊しても通りました
- 削除する範囲を広く指定して、日付の検査の関数まで消しました。型検査で分かりました
- `npm run check` が上限に届きかけています。step を複製して全部を検査する方式の代償を、早く測っていませんでした
- 人の全文確認も、独立したレビューも受けていません

## Try

- 計画の文書は、実装より先に作ります
- 「何かが起きない」ことのテストは、通知ではなく、状態を比べます
- コードを書き換える範囲は、目印ではなく、定義の単位で指定します

## 関連ドキュメント

- [Unit 6 の Bolt 計画](iteration_plan-typescript-fp-6.md)
- [Unit 6 の Bolt 終了報告](bolt_report-typescript-fp-6.md)
