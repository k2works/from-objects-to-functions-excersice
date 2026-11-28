---
type: ADR
title: "ADR-061 TypeScript FP 版はハブの依存を Effect で表し、失敗は素の型の和にする"
description: "Zettai 連載 TypeScript FP 版で、ハブの依存を Effect で表し、業務の失敗を素の型の和にして、取得の失敗の理由をハブが付ける決定。Data.TaggedError との比較と代償を記録する。後に人がまとめて承認した。"
tags: [adr, typescript, zettai, effect, error-handling]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-28T07:34:53Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-28T07:34:54Z }
---

# ADR-061 TypeScript FP 版はハブの依存を Effect で表し、失敗は素の型の和にする

日付: 2026-11-28

## ステータス

2026-11-28 承認されました（実装と章の公開の後に、人がまとめて承認しました）

それまでの経緯: ハブの依存の型は、2026-11-28 に Unit 4 の Bolt 計画の承認で決まりました。失敗の型の表し方と、取得の失敗を付ける場所は、同じ日に推奨案を採用しました。後の 2 つは、利用者の事前の指示によるもので、人が個別に確認してはいません

## コンテキスト

第 6 章でハブが保管を読み書きするようになり、第 7 章で取得の失敗を型に出します。次の 3 つを決めます。

1. ハブが外に頼る操作の型
2. 業務の失敗の型の表し方
3. 取得の失敗に理由を付ける場所

### 先に確かめたこと

2026-11-28、Effect 4.1.6 で確かめました。

| 項目 | `Data.TaggedError` | 素の型の和 |
| :--- | :--- | :--- |
| `Error` のインスタンスで、スタックを持つ | はい | いいえ |
| `Effect.gen` の中で、`yield*` だけで型付きの失敗になる | はい | `Effect.fail` に渡す |
| `Effect.catchTag` で、種類を指定して扱える | はい | はい |

第 7 章で取得の型を変えた結果、step 4 から変わったファイルは、`src/` が 4（`errors.ts`・`handle.ts`・`hub.ts`・`web/app.ts`）、既存のテストが 3 でした。

## 決定

1. **ハブの依存（`ToDoListFetcher`・`StateFetcher`・`EventPersister`）は、`Effect` を返す関数にする。** 保管の読み書きは副作用で、保存の後は取得の結果が変わる
2. **業務の失敗は、イベントやコマンドと同じ、`_tag` を持つ素の型の和にする**
3. **アダプタは「あるか無いか」を `Option` で返し、`ListNotFound` はハブが付ける。** `ToDoListHub.getList` の型は `Effect<ToDoList, ListNotFound>` にする
4. **使い分け:** 実行を伴わない核は `Result`、保管を読み書きする境界は `Effect` の `E` にする。`Effect.fromResult` でつなぐ

Unit 4 の Bolt 計画の受入条件は「取得は `Result` を型に出す」でした。ハブの取得は保管を読むので、`Result` ではなく `Effect` の `E` に出しました。

### 検討した代替案

| 案 | 採らなかった理由 |
| :--- | :--- |
| `Data.TaggedError` | スタックと `yield*` の利点は、この章のコードでは使わない。核の値は、出来事やコマンドと同じ形にそろえる。第 12 章で原因の記録が要る場合に、確かめ直す |
| 取得の依存の戻り値を `Effect<ToDoList, ListNotFound>` にする | すべてのアダプタが、業務の失敗を組み立てることになる |
| 取得の戻り値を `Effect<Result<ToDoList, ListNotFound>>` にする | 失敗が 2 段になる。呼び出し側は、実行した後にもう一度分岐する |
| ハブの依存を、第 9 章まで同期の関数にする | **見込み:** 第 9 章でもう一度型を変えることになる |

## 影響

### ポジティブ

- 失敗の値を、テストで普通の値として比べられる
- アダプタは、業務の失敗を知らない

### ネガティブ

- 失敗の値は `Error` ではないので、スタックを持たない
- ハブのテストと経路は、`Effect` を実行する必要がある
- `StateFetcher` は引数の無い関数で、`@effect/tsgo` は「`Effect` はもともと遅延するので、関数で包まなくてよい」と提案を表示する。設計文書の形に合わせて、関数のままにする

## コンプライアンス

- 取得の成功と失敗を、理由の中身まで比べるテストがある
- web が domain から読み込むファイルを、テストで固定している

## 備考

- 著者: anthropic/claude-sonnet-5-5
- 関連 ADR: [ADR-058](ADR-058-typescript-dependency-by-function-argument.md)、[ADR-060](ADR-060-typescript-typed-errors-and-defects.md)
