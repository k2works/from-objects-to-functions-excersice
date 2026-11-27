---
type: ADR
title: "ADR-057 TypeScript FP 版は受け入れテストの経路を、driver を渡す高階関数で差し替える"
description: "Zettai 連載 TypeScript FP 版で、受け入れテストの経路を、Promise を返す driver を渡す高階関数として定義する決定。Context.Service と Layer、Effect と Scope を使う 2 案と比べた結果、検出できる範囲に差が無かったこと、代償と確かめていないことを記録する。"
tags: [adr, typescript, zettai, testing]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-27T21:00:23Z }
---

# ADR-057 TypeScript FP 版は受け入れテストの経路を、driver を渡す高階関数で差し替える

日付: 2026-11-27

## ステータス

2026-11-27 承認されました（Unit 2 のゲート ② で、人が推奨の案 A を選びました）

## コンテキスト

[ADR-055](ADR-055-typescript-testing-tools.md) で、受け入れテストは `describe.each` で経路を差し替える形を自作すると決めました。第 3 章では、経路を何の形で表すかを決めます。

### 先に確かめたこと

同じ 2 つのシナリオを、domain と HTTP の 2 経路で 3 通りに書きました。条件と全結果は、[Unit 2 の Bolt 計画](../development/iteration_plan-typescript-fp-2.md)の「経路の差し替え方の比較」にあります。

| 案 | 形 | 経路の定義 + シナリオ |
| :--- | :--- | :--- |
| A（採用） | `Promise` を返す driver を、`(store, use) => Promise<A>` で渡す | 35 + 29 行 |
| B | driver を `Context.Service` にし、`Layer` で差し替える | 35 + 27 行 |
| C | `Effect` を返す driver を、`Scope` を要求する `Effect` で作る | 31 + 32 行 |

行数は、3 案で共通の HTML の読み戻しを含みません。

- **検出できる範囲に、3 案の差はありませんでした。** 4 種類の壊し方で、失敗する経路が一致しました
- HTTP の経路で操作名を書き間違えると、3 案とも型検査が失敗しました
- B と C を比較で書いた形は、操作のたびに HTTP のサーバーを起動しました

## 決定

1. **経路は、保管するリストと、driver を使う処理を受け取る高階関数にする。** 型は `<A>(store, use: (zettai: ZettaiDriver) => Promise<A>) => Promise<A>`
2. **driver は、業務の言葉の操作を `Promise` で返す。** シナリオは `async`・`await` で書く
3. **後始末は、経路の中で `Effect.scoped` が担う。** 失敗したシナリオの後も停止することを、テストで固定する
4. **HTTP の経路の読み戻しは、200 と 404 のほかの状態と、エスケープされていない値を失敗にする**

### 検討した代替案

| 案 | 採らなかった理由 |
| :--- | :--- |
| B | 依存を `Layer` で差し替える方法は、第 4 章の主題である。シナリオを続けて書くには、全体を 1 つの `Effect` にする必要がある |
| C | 必要な後始末が型に現れる利点はあるが、シナリオを `Effect` で書くことになる。この章の主題は高階関数である |

## 影響

### ポジティブ

- シナリオを、普通の `async` の関数として読める
- 第 3 章の「高階関数を使う」を、経路そのもので説明できる

### ネガティブ

- 経路の境目で `Effect` から `Promise` に変わる。サーバーの起動の失敗は、型付きエラーではなく Promise の失敗になる
- 経路の型 `Route` は、後始末の約束を表さない。経路の実装で `Effect.scoped` を忘れると型検査が失敗するが、閉じないスコープを渡すと型検査も lint も通る（第 3 章の実装の後に確かめた）。テストで守る
- HTML の読み戻しは、いまの画面の構造（`<li>`）に依存する

## コンプライアンス

- 同じシナリオが、domain と HTTP の 2 経路で実行されている
- 経路の選択が、テストのコードだけにある
- HTTP の経路が、失敗したシナリオの後もサーバーを停止することを確かめるテストがある

## 備考

- 著者: anthropic/claude-sonnet-5-5
- 関連 ADR: [ADR-055](ADR-055-typescript-testing-tools.md)、[ADR-056](ADR-056-typescript-http-with-stable-parts.md)
