---
type: ADR
title: "ADR-059 TypeScript FP 版は状態変換のモノイドを、自作の関数で表す"
description: "Zettai 連載 TypeScript FP 版で、状態変換のモノイドを自作の型と関数で表す決定。Effect の Reducer と比べ、法則が型で保証されないことと、合成した変換の適用に件数の限界があることを理由に、本体の状態は reduce で作る。第 5 章の全文確認で承認された。"
tags: [adr, typescript, zettai, effect, monoid]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-28T02:29:19Z }
---

# ADR-059 TypeScript FP 版は状態変換のモノイドを、自作の関数で表す

日付: 2026-11-28

## ステータス

2026-11-28 承認されました。承認ゲートの対象にしない判断として、第 5 章の全文確認で確認を受けました（[Unit 3 の Bolt 計画](../development/iteration_plan-typescript-fp-3.md)）

## コンテキスト

第 5 章で、状態から状態への変換 `StateTransition` と、その合成 `andThen`、何もしない変換 `identityTransition` がモノイドになることを示します。計画では、法則を先に自作の型のテストで示し、Effect の `Reducer` に置き換えるかは、比べてから決めることにしました。

### 先に確かめたこと

2026-11-28、Effect 4.1.6 で確かめました。

- `effect` の `dist` に、名前に `monoid`・`semigroup` を含むファイルはありません。`Reducer<A>` は `combine`・`initialValue`・`combineAll` を持つ値です
- `Reducer.make(andThen, identityTransition).combineAll` は、生成した出来事の列で、`replay` と同じ状態を返しました（500 回）
- `Reducer.make` に `combineAll` を渡さない場合、初期値から順に `combine` する実装が使われます（配布された JavaScript を読みました）
- `Reducer.flip` は、結合する順序を逆にした `Reducer` を返しました
- **`Reducer.make` は、結合律を満たさない `combine`（引き算）も受け取りました。** 型が表すのは `combine` と初期値の形で、法則ではありません

## 決定

1. **`StateTransition`・`andThen`・`identityTransition` を、自作の型と関数にする。** `Reducer` は使わない
2. **法則は、性質テストで確かめる。** 順序は、法則のテストとは別に、畳み込みの結果と比べて固定する
3. **本体で状態を作るのは、`events.reduce(apply, emptyState)`（`replay`）にする。** 合成した変換を適用する形は、本体で使わない

### 検討した代替案

| 案 | 採らなかった理由 |
| :--- | :--- |
| `Reducer<StateTransition>` を使う | いまは、モノイドを値として受け取る汎用の処理がない。`Reducer` にしても、法則のテストは同じだけ要る |
| 本体の `replay` を `transitionOfAll` で作る | 開発機の Node で、9,376 件の出来事を合成して適用すると `RangeError` になった。`reduce` で状態を順に作る形は、20 万件で通った |

## 影響

### ポジティブ

- 合成の定義が数行の関数で読め、法則との対応が見える
- 本体の畳み込みは、件数で呼び出しが積み重ならない

### ネガティブ

- `Reducer` を受け取る Effect の機能は、そのままでは使えない。必要になったら `Reducer.make(andThen, identityTransition)` で作れる
- モノイドの形（結合と単位元の組）は、1 つの型としては表れない。`andThen` と `identityTransition` の組であることは、名前とテストで示す
- 状態の作り方が 2 つある（`replay` と `transitionOfAll`）。同じ結果になることは、性質テストで守る。保証するのは、生成した入力の範囲である

## コンプライアンス

- 結合律、左右の単位元、起きた順の合成を確かめる性質テストがある
- 生成する出来事の列に、既にあるリストへの追加が含まれることを確かめるテストがある
- 本体（`src/`）が `transitionOfAll` で状態を作らないことは、テストでは固定していない。レビューで確かめる

## 備考

- 著者: anthropic/claude-sonnet-5-5
- 関連 ADR: [ADR-053](ADR-053-typescript-effect-version-and-stability.md)、[ADR-055](ADR-055-typescript-testing-tools.md)
