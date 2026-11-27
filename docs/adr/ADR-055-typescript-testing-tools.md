---
type: ADR
title: "ADR-055 TypeScript FP 版は vitest と fast-check でテストし、受け入れテストを自作する"
description: "Zettai 連載 TypeScript FP 版で、テストに vitest、性質テストに fast-check、結合テストに Testcontainers を使い、受け入れテストは vitest の describe.each で経路を差し替える形を自作する決定。@effect/vitest・Arbitrary・Cucumber を選ばなかった理由と代償を記録する。"
tags: [adr, typescript, zettai, testing]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-27T09:25:01Z }
---

# ADR-055 TypeScript FP 版は vitest と fast-check でテストし、受け入れテストを自作する

日付: 2026-11-27

## ステータス

2026-11-27 承認されました（Unit 1 のゲート ③ で、人が推奨の案を選びました）

## コンテキスト

単体テスト、性質テスト、受け入れテスト、結合テストの道具を決めます。Effect 向けの `@effect/vitest` と、Effect の性質テストの `Arbitrary` は、どちらも unstable です（[ADR-053](ADR-053-typescript-effect-version-and-stability.md)）。

### 先に確かめたこと

条件と全結果は、[Unit 1 の Bolt 計画](../development/iteration_plan-typescript-fp-1.md) の「S6〜S8 の結果」にあります。

| 比べたもの | 結果 |
| :--- | :--- |
| 性質テストを含む 3 本（空行を除く行数 / 追加される package） | `@effect/vitest` + `Arbitrary` は 24 行 / 1、vitest + `fast-check` は 30 行 / 2、vitest + `Arbitrary` は 25 行 / 0。3 案とも、偽の性質で失敗して反例を表示した |
| unstable の指摘 | `@effect/vitest` か `Arbitrary` を使う 2 案は、`effect-tsgo tsc` が失敗した |
| 受け入れシナリオ 1 本を 2 経路で実行（経路の切り替え 18 行を除く） | 自作は 8 行 / 追加 0、Cucumber は 24 行 / 追加 86。どちらも、期待値を誤ると 2 経路とも失敗した |
| Testcontainers | `postgres:18-alpine` の起動と問い合わせが通った。Docker に接続できない場合は skip されず失敗した |

### 試していないこと

- `@effect/vitest` の `it.effect` が用意する時刻の操作
- 受け入れテストの HTTP の経路で、実サーバーを通すこと
- 複数のシナリオや、状態を持つシナリオでの 2 方式の差
- `fast-check` で、`Schema` から生成器を作ること
- CI（Linux）での Testcontainers の実行

## 決定

1. **テストは vitest 5.1.2 で書く。** `Effect` は、テストの中で `Effect.runPromise` などを呼んで実行する
2. **性質テストは `fast-check` 4.11.0 を直接使う。** 回数は `const TRIALS = N` で宣言する
3. **受け入れテストは自作する。** 経路ごとの driver を同じ型にそろえ、vitest の `describe.each` で同じシナリオを実行する
4. **結合テストは `@testcontainers/postgresql` 12.2.2 を使う。** Docker が無い場合は skip せず、失敗させる
5. `@effect/vitest`、`Arbitrary`、`@cucumber/cucumber` は入れない

### 検討した代替案

| 案 | 採らなかった理由 |
| :--- | :--- |
| `@effect/vitest` + `Arbitrary` | 短く書けるが、unstable の承認と、テストファイルごとの除外が必要になる。除外はそのファイルのほかの unstable な使用も通す |
| vitest + `Arbitrary` | 依存は増えないが、unstable の扱いは同じである |
| Cucumber | シナリオを Gherkin の文章で読め、Kotlin FP 版と直接比べられる。ただし依存が 86 増え、経路ごとの実行を品質ゲートに足す必要がある。第 3 章の主題である受け入れテストの抽象化を、自分のコードで示すことを優先した |

## 影響

### ポジティブ

- テストのコードに、unstable の除外が要らない
- 受け入れテストの経路の型を、型検査で確かめられる
- すべてのテストが `vitest` の 1 つの段で実行される

### ネガティブ

- `Effect` を扱うテストは、実行の呼び出しを自分で書く
- `Schema` と生成器を、別々に書く
- シナリオは、文章ではなくコードとして読むことになる
- Kotlin FP 版とは、受け入れテストの道具が違う

## コンプライアンス

- 性質テストの回数が `const TRIALS = N` で宣言されている
- 同じシナリオが、2 つ以上の経路で実行されている
- Docker に接続できない状態で結合テストが失敗することを、壊して確かめる

## 備考

- 著者: anthropic/claude-sonnet-5-5
- 関連 ADR: ADR-053、[ADR-054](ADR-054-typescript-toolchain-and-check.md)
