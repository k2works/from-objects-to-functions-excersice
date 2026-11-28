---
type: Retrospective
title: "Unit 5 のふりかえり（KPT）- Zettai 連載（TypeScript FP 版）"
description: "TypeScript FP 版 Unit 5 の第 8〜9 章についての KPT ふりかえり。"
tags: [development, retrospective, zettai, typescript, effect, unit-5]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-28T05:22:54Z }
---

# Unit 5 のふりかえり（KPT）- Zettai 連載（TypeScript FP 版）

## Keep

- 実装の前に、データベースが受け付ける文字列を確かめました。2 つの保管の振る舞いのずれが、実装の前に分かりました
- 同じ型の 2 つの保管に、同じ契約のテストを当てました
- 生成する入力を数えました。**完了の項目が 1 件も無いことは、この確認で分かりました**
- ライブラリの追加が拒否されたとき、別の手段で回避せず、止めて報告しました

## Problem

- 「完了を含む列がある」テストの条件が緩く、2,000 列に 1 列でも通りました
- ライブラリの追加を、step の複製や調査と 1 つのコマンドにまとめていました。何が拒否されたのかが分かりにくくなりました
- ゲート ⑤ は、代替案を実装して比べていません
- `ORDER BY` の欠落を見つけるテストがありません
- 長いシェルのコマンドが、引用符の解析で何度か失敗しました
- 人の全文確認を受けないまま、章を重ねています。独立したレビューもしていません

## Try

- 生成の割合のテストは、割合の下限を決めます
- 外部のライブラリを追加する操作は、単独のコマンドにします
- 長いファイルは、書き込みのツールで作ります

## 第 10 章に向けて

- 読み取り、判断、追記を 1 つのトランザクションにした場合に、同時の 2 つのコマンドの片方が必ず断られることを、実際の PostgreSQL で確かめます

## 関連ドキュメント

- [Unit 5 の Bolt 計画](iteration_plan-typescript-fp-5.md)
- [Unit 5 の Bolt 終了報告](bolt_report-typescript-fp-5.md)
