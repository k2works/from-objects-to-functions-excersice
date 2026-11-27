---
type: ADR
title: "ADR-053 TypeScript FP 版は Effect 4.1.6 に固定し、stable を原則にする"
description: "Zettai 連載 TypeScript FP 版で、fp-ts ではなく Effect v4 を使い、版を 4.1.6 に固定する決定。stable な API を原則とし、unstable な API は代替と比べて承認したものだけを adapter に閉じ込めて使う。選ばなかった 2 案と代償を記録する。"
tags: [adr, typescript, zettai, effect]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-27T09:25:01Z }
---

# ADR-053 TypeScript FP 版は Effect 4.1.6 に固定し、stable を原則にする

日付: 2026-11-27

## ステータス

2026-11-27 承認されました（Unit 1 のゲート ① で、人が案 A を選びました）

## コンテキスト

TypeScript FP 版は、参考実装（fp-ts 2.16）の説明順を借りて、同じ 13 章を書きます。着手前に、使うライブラリと版、そして安定性の扱いを決める必要があります。この決定は、第 2 章の HTTP、第 9 章の SQL、テストの道具の選び方を縛ります。

### 先に確かめたこと

確かめ方とコードの範囲は、[Unit 1 の Bolt 計画](../development/iteration_plan-typescript-fp-1.md) の「S1・S2 の結果」にあります。

| 項目 | 結果 |
| :--- | :--- |
| fp-ts | [公式 README](https://github.com/gcanti/fp-ts/blob/master/README.md) が Effect-TS エコシステムへの合流を告知し、Effect-TS を fp-ts v2 の後継と位置づけている |
| Effect の版 | latest は 4.1.6。4.0.0（2026-10-01）から 4.1.6（2026-11-26）まで、56 日で 15 回リリースされた |
| 安定性の約束 | `@stability unstable` の API は、minor リリースで壊れる変更が入りうる（[MIGRATION.md](https://github.com/Effect-TS/effect/blob/main/MIGRATION.md)）。import path には現れず、タグで示される |
| stable なもの | `Effect`・`Result`・`Option`・`Context`・`Layer`・`Scope`・`Schema`・`Exit`・`Cause` など。本体直下は stable 120、unstable 22、experimental 3 |
| unstable なもの | `Arbitrary`、`effect/http`（31 モジュール）、`effect/sql`（10）、`effect/schema`（5。本体の `effect/Schema` とは別）。`effect` 以外の package はすべて（`@effect/platform-node`・`@effect/sql-pg`・`@effect/vitest` を含む） |
| 依存の数 | `effect` だけは 1 package。`@effect/platform-node` を足すと 14 package（peer の `redis` を含む） |
| 最小の HTTP | `effect/http` の `toWebHandler` でも、stable な部品だけで書いた `Request` から `Effect<Response>` の関数でも、サーバーを起動せずに応答を確かめられた |

### 試していないこと

- unstable な API が、これまでのリリースで実際に壊れた回数
- Node のサーバーへの接続（`effect/http` と stable な部品の、どちらの場合も）
- `effect/sql` と `pg`
- `Schema`・`Arbitrary` の使い勝手
- 移行ガイドの `schema.md` は読んでいない。`v3-to-v4.md` は一部を検索しただけである

## 決定

1. **中心のライブラリは Effect v4 とし、fp-ts は使わない。**
2. **版は 4.1.6 に固定する。** `^`・`~` を使わず、lock をコミットする。更新するかは各 Unit の冒頭で判断し、更新する場合は全テストを通してから別のコミットにする
3. **stable な API を原則にする。**
4. **unstable な API は、代替と比べて人が承認したものだけを使う。** 比較と承認は、その API が必要になる章やステップのゲートで行う
   - HTTP は第 2 章、SQL は第 9 章のゲートで決める
   - `@effect/vitest` と `Arbitrary` は、Unit 1 のゲート ③ で決める
5. **承認した unstable な API は adapter に閉じ込める。** domain のコードからは import しない。記事では、unstable であることを本文で示す

### 検討した代替案

| 案 | 内容 | 採らなかった理由 |
| :--- | :--- | :--- |
| B | unstable を第一候補にし、adapter に閉じ込める | Effect らしい構成をそのまま見せられるが、minor 更新で記事とコードが壊れる可能性を、比べる前に引き受けることになる |
| C | unstable を使わない | 壊れる可能性は最も小さいが、Node のサーバー接続と SQL の包み方の自作が増える（行数は測っていない）。`@effect/vitest` と `Arbitrary` も、比べずに候補から外れる |

## 影響

### ポジティブ

- 第 1 章は stable な範囲だけで書ける見込みである
- HTTP、SQL、テストの道具を、実物を比べてから決められる
- 更新で壊れる範囲が、承認したものと adapter に限られる

### ネガティブ

- 第 2 章と第 9 章に、比較の作業が残る
- stable な部品で代替する場合は、自作する部分が増える
- 版を固定するため、修正の取り込みは意図して行う必要がある
- stable の約束はライブラリ側の方針であり、壊れないことを保証するテストは自分で持つ必要がある

## コンプライアンス

- `apps/typescript-fp/zettai/` の `package.json` で、直接依存に `^`・`~` が無い
- unstable な API の import は、adapter の中だけにあり、承認が ADR に残っている
- **追記（2026-11-27、ゲート ② の後に確認）**: [ADR-054](ADR-054-typescript-toolchain-and-check.md) の 1 段目 `effect-tsgo tsc` は、unstable な API の使用を指摘して失敗する。承認した箇所は、除外コメントで示す。**除外コメントを置く場所が adapter の中かどうかは、機械では確かめていない。** 版の固定（`^`・`~` が無いこと）を確かめる手段も、まだ無い
- 記事が unstable な API を使う箇所は、本文で示されている

## 備考

- 著者: anthropic/claude-sonnet-5-5
- 関連 ADR: ADR-054（予定。実行方法と `npm run check`）、ADR-055（予定。テストの道具）
