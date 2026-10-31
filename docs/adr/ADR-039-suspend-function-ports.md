---
type: ADR
title: "ADR-039 関数型 DI は suspend の関数型と constructor function にし、runBlocking は web の最外周に置く"
description: "Zettai 連載 Kotlin FP 版で、port を interface ではなく suspend の関数型で表し、Hub を関数プロパティを持つ値と constructor function で組み立てる決定。必須の port に既定値を置かない理由、末尾 lambda の落とし穴、runBlocking を同期 HTTP の境界だけに置く理由を記録する。"
tags: [adr, kotlin-fp, zettai, coroutines]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-10-31T06:41:16Z }
verified:
  - { by: human:kakimomokuri, at: 2026-10-31T06:40:40Z }
---

# ADR-039 関数型 DI は suspend の関数型と constructor function にし、runBlocking は web の最外周に置く

日付: 2026-10-31

## ステータス

2026-10-31 提案されました

2026-10-31 承認されました

## コンテキスト

第 4 章で依存性の注入を入れます。Kotlin FP 版は `suspend` を使えるので、port の形に選択肢があります。永続化で I/O が入る第 9 章でも、第 1〜8 章のコードを書き換えずに済む形を、先に決めておく必要があります。

## 決定

1. **port は `suspend` の関数型にする。** `typealias ToDoListFetcher = suspend (User, ListName) -> Either<ZettaiError, ToDoList>` のように、interface を作らず関数型に名前を付ける
2. **Hub は関数プロパティを持つ値とし、constructor function で組み立てる。** `todoListHub(fetchList, fetchState, persist)` が依存を配線する
3. **command に必要な port は必須にし、既定値を置かない。** 空状態や何もしない persister を既定値にすると、配線漏れでも成功を返し、event が失われる
4. **呼び出し側は名前付き引数を使う。** 末尾 lambda は最後の parameter に束縛される。parameter を 3 つに増やした結果、前の `todoListHub { ... }` という呼び出しは `persist` に渡る形になり、型が合わず compile error になった
5. **domain の純粋関数は `suspend` にしない。** `handle` と `replayFrom` は通常の関数のままにし、`suspend` は application の port と adapter の境界だけにする
6. **`runBlocking` は同期の http4k handler と `suspend` の port をつなぐ web の最外周 1 箇所だけに置く。** domain と application には coroutine runner を持ち込まない

この ADR の例は、第 4〜7 章の形（`ToDoListFetcher`、`todoListHub(fetchList, fetchState, persist)`）です。第 8・9 章で port の名前と型を変えました（`ToDoListFetcher` の廃止、読み出しの port の集約単位化、戻り値の `Either<PersistenceFailure, ...>`）が、port を `suspend` の関数型にし、constructor function で配線する考え方は変わっていません。

## 検討した案

| 案 | 判断 |
| :--- | :--- |
| `fun interface` や通常の interface | 採らない。差し替えるだけの依存には、関数型で足りる。mock のための型が増える |
| DI コンテナ | 採らない。constructor function の引数で、依存が型として読める |
| 全関数を `suspend` にする | 採らない。純粋な core が見えなくなり、domain が coroutine に依存する |
| 非同期 HTTP server へ切り替える | 採らない。比較のために http4k を維持する |

## 結果

- 差し替えは関数を渡すだけで済みます。in-memory と HTTP の差し替えテストを、mock ライブラリなしで書けます。
- `runBlocking` は同期 HTTP の 1 箇所に限られ、境界テストが domain と application での import を禁じます。
- blocking の JDBC を `suspend` の port の後ろに置くと、`runBlocking` の中でスレッドをふさぎます。この扱いは第 9 章のスパイクで決め、別の ADR に残します。

## 関連

- [ADR-038 Arrow Core を typed error の表現と合成に限って使う](ADR-038-arrow-core-for-typed-errors.md)
- [ADR-030 関数型 DI は impl Fn、並びに入れる変換は Box<dyn Fn> にする](ADR-030-functional-di-shape.md)（Rust 版。同じ問いに別の言語で答えた例）
