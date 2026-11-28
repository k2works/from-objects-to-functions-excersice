---
type: Retrospective
title: "Unit 7 のふりかえり（KPT）- Zettai 連載（TypeScript FP 版）"
description: "Zettai 連載 TypeScript FP 版の Unit 7 のふりかえり。計画が実装の後になったこと、確かめる前の主張、確認を受けないまま章を積んだことを記録する。"
tags: [development, retrospective, zettai, typescript, effect, unit-7]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-28T06:28:07Z }
---

# Unit 7 のふりかえり（KPT）- Zettai 連載（TypeScript FP 版）

## Keep

- 「型にある項目しか載らない」という思い込みを、壊して確かめて、本文と実装を直しました
- 通った往復のテストを疑い、生成器の範囲の不足を見つけました
- 総括を過去の章の要約にせず、最後の step を壊し直しました。どの検査も止めない所が分かりました
- 満たしていない完了条件を、未達のまま記録しました

## Problem

- **Unit 7 の計画を、第 12 章の実装と執筆の後に書きました。** Unit 6 の Try を守れませんでした
- コードのコメントに、確かめる前の主張（型にある項目しか載らない）を書きました
- `npm run check` が上限を超えました。step の複製が時間に与える影響を、早い段階で見積もっていませんでした
- 第 6 章から、人の確認も CI も受けないまま章を積み重ねました。誤りがあれば、後の章にも影響します

## Try

- 計画の文書が無い状態では、step を追加しません。step の追加の前に、計画の有無を確かめます
- 型が何かを防ぐと書く前に、その型で通る誤りを 1 つ書いて確かめます
- 累積の step を持つ構成では、最初の Unit で、step の数と検査の時間の関係を測ります
- 確認待ちの章が 2 章を超えたら、利用者に確認を依頼します

## 連載全体を通して

| 項目 | 内容 |
| :--- | :--- |
| 書いた章 | 13 章。確認済みは第 1〜5 章 |
| 同期停止したゲート | 12 箇所のうち 6 箇所（Unit 1〜3） |
| 完了した Unit | 7 のうち 3 |

## 関連ドキュメント

- [Unit 7 の Bolt 計画](iteration_plan-typescript-fp-7.md)
- [Unit 7 の Bolt 終了報告](bolt_report-typescript-fp-7.md)
