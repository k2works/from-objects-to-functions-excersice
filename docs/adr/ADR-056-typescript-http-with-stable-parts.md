---
type: ADR
title: "ADR-056 TypeScript FP 版の HTTP は stable な部品だけで書き、node:http への接続を自作する"
description: "Zettai 連載 TypeScript FP 版で、HTTP を Web 標準の Request と Response の関数として書き、node:http への接続を自作する決定。unstable な effect/http と @effect/platform-node を使わない理由と、実サーバーで比べた 3 案の結果、代償を記録する。"
tags: [adr, typescript, zettai, effect, http]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-27T10:16:28Z }
---

# ADR-056 TypeScript FP 版の HTTP は stable な部品だけで書き、node:http への接続を自作する

日付: 2026-11-27

## ステータス

2026-11-27 承認されました（Unit 2 のゲート ① で、人が案 A を選びました）

## コンテキスト

第 2 章で、`GET /todo/{user}/{listname}` が HTML を返す最小の縦串を通します。Effect の HTTP の機能（`effect/http`）と、Node 用のサーバー（`@effect/platform-node`）は、どちらも unstable です。[ADR-053](ADR-053-typescript-effect-version-and-stability.md) は、unstable な API を使う場合に、代替と比べて承認することを求めています。

### 先に確かめたこと

3 案のサーバーを実際に起動し、`node:http` のクライアントから同じ要求を送りました。条件と全結果は、[Unit 1 のふりかえり](../development/retrospective-typescript-fp-1.md)の「HTTP の方式の事前比較」と、[Unit 2 の Bolt 計画](../development/iteration_plan-typescript-fp-2.md)の「HTTP の 3 案」にあります。

| 比較 | A（採用） | B | C |
| :--- | :--- | :--- | :--- |
| 構成 | stable な部品だけ。接続も自作 | `effect/http` のルーター。接続は自作 | `effect/http` と `@effect/platform-node` |
| `effect-tsgo` の unstable の指摘 | 0 件 | 5 件 | 6 件 |
| 空行とコメントを除く行数（業務の部分を除く） | 39 | 49 | 32 |
| 実行時の package 数 | 1 | 1 | 14 |
| 壊れた URL エンコード | 400（自分で分岐） | 404 | 404 |
| HTML の `charset` | 自分で指定した | 既定では付かない | 既定では付かない |

一覧の表示と、日本語や特殊文字を含む名前には、3 案とも応答しました。

### 試していないこと

- 性能、同時の要求、大きな要求、停止中の要求の扱い
- 本文のある要求（フォームの POST）。3 案とも試していない
- `effect/http` が HTML の値をエスケープするか
- Linux での 3 案の比較

## 決定

1. **HTTP は、Web 標準の `Request` を受け取り、`Effect<Response>` を返す関数として書く。**
2. **`node:http` への接続は自作する。** 起動と停止を 1 組にして扱う
3. **`effect/http` と `@effect/platform-node` は使わない。** ライブラリは追加しない
4. 接続の自作は、まず `GET` と本文の無い要求だけを扱う。本文の読み取りは、必要になる章で足す

### 検討した代替案

| 案 | 採らなかった理由 |
| :--- | :--- |
| B | unstable を使いながら接続を自作するため、3 案で行数が最も多い |
| C | 最も短く書けるが、unstable な package を 2 系統と、実行時の依存を 14 引き受ける |

## 影響

### ポジティブ

- unstable の除外コメントと、その置き場所を守る仕組みが要らない
- 「関数を使って HTTP を扱う」という章の主題を、自分のコードで示せる
- 実行時の依存が `effect` だけになる

### ネガティブ

- ルーティングと接続を自作し、その誤りは自分のテストで見つける必要がある
- 本文の読み取りを足す作業が、後の章に残る。大きな本文や、途中で切れた要求への対処も自分で決める
- 性能や同時の要求への対処は、この実装の範囲では扱わない。**本番で使える HTTP サーバーではない**

## コンプライアンス

- `effect/http` と `@effect/platform-node` の import が無いこと（unstable の使用は、型検査が拒否する）
- domain のコードが、`node:http` と web の module を import しないことを、テストで固定する
- 実サーバーを起動するテストがあること

## 備考

- 著者: anthropic/claude-sonnet-5-5
- 関連 ADR: [ADR-053](ADR-053-typescript-effect-version-and-stability.md)、[ADR-054](ADR-054-typescript-toolchain-and-check.md)
