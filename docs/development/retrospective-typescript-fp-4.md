---
type: Retrospective
title: "Unit 4 のふりかえり（KPT）- Zettai 連載（TypeScript FP 版）"
description: "TypeScript FP 版 Unit 4 の第 6〜7 章についての KPT ふりかえり。"
tags: [development, retrospective, zettai, typescript, effect, unit-4]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-28T04:52:33Z }
---

# Unit 4 のふりかえり（KPT）- Zettai 連載（TypeScript FP 版）

## Keep

- 計画の前に設計文書の名前と挙動を調べ、照合の表を計画に載せました（前回の Try）
- 章ごとに step を分けたので、公開前の第 6 章を、第 7 章の変更で直さずに済みました
- 法則と網羅のテストを壊して確かめました。**状態の追加を見逃すテストは、この確認で見つかりました**
- 生成する入力に、受け付けと拒否の両方が含まれることを、テストにしました

## Problem

- 最初の状態の移り方のテストは、「組が 16 通り」を数えるだけで、状態が増えても通りました
- `server.test.ts` が 1 回失敗しました。再現できず、原因を確かめていません
- 人の全文確認を受けないまま、2 章を重ねました。確認で直しが出ると、後の章に波及する可能性があります
- 章の中身に対する、独立したレビューはしていません

## Try

- 型で網羅する一覧は、実装の側だけでなく、テストの側にも置きます
- 人の確認が後になる章は、主張ごとに、確かめたコマンドと結果を計画か報告に残します
- まれな失敗は、次に起きたときに、出力を保存して調べます

## 第 8 章に向けて

- 射影は、状態の畳み込みと同じ出来事の列から作ります。2 つの結果が一致することを性質テストにします

## 関連ドキュメント

- [Unit 4 の Bolt 計画](iteration_plan-typescript-fp-4.md)
- [Unit 4 の Bolt 終了報告](bolt_report-typescript-fp-4.md)
