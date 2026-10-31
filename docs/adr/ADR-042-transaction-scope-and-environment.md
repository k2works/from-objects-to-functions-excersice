---
type: ADR
title: "ADR-042 transaction の境界を command 1 つの処理に置き、環境を型引数で通す"
description: "Zettai 連載 Kotlin FP 版の第 10 章で、transaction の境界を command 1 つの処理に置く決定と、transaction の環境を暗黙の context ではなく型引数と関数の引数として通す決定。Resource を使わず use で足りる理由と、既存 Kotlin 版 ADR-011 との違いを記録する。"
tags: [adr, kotlin-fp, zettai, transaction, postgresql]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-10-31T11:37:33Z }
---

# ADR-042 transaction の境界を command 1 つの処理に置き、環境を型引数で通す

日付: 2026-10-31

## ステータス

2026-10-31 提案されました

## コンテキスト

第 9 章の store は、操作ごとに connection を開いて閉じます。そのため、command の処理（読み出し、判断、保存）は 3 つの別々の transaction になります。同じリストへの command が並行すると、2 つとも古い状態を読んで判断し、両方が追記されます。この問題はテストで再現しました（`ConcurrentCommandTest`）。

決めることは 3 つあります。

1. transaction の境界をどこに置くか
2. transaction の環境（connection）を、読み出しと保存の操作へどう渡すか
3. connection の寿命を `Resource` で表すか、`use` で足りるか

既存 Kotlin 版の [ADR-011](ADR-011-transaction-boundary.md) は、境界を command 1 つの処理に置き、ポートの文脈を中身の空の `TxContext` にしました。その過程で設計を 2 回間違えています。Kotlin FP 版は、`ContextReader` を自作せず、Kotlin の関数の引数で同じ問いに答えます。

## 決定

1. **境界は command 1 つの処理に置く。** コマンドは業務上の 1 つの意図なので、読み出し、判断、保存が 1 つの単位になる。HTTP のリクエスト 1 つに広げる案は採らない
2. **環境は型引数 `Tx` と、関数の引数で通す。** transaction の中で動く操作は `suspend (Tx, ...) -> Either<...>` という関数型になり、`Tx` を持たない場所からは呼べない。境界は `transaction { tx -> ... }` というブロックとして、コードの 1 か所に現れる
3. **`Tx` を知るのは store の実装だけにする。** PostgreSQL では `java.sql.Connection`、in-memory では `Unit`。Hub は `Tx` を型引数に持つ関数として組み立てるが、組み立てた `ToDoListHub` の型には `Tx` が出ない
4. **connection は transaction の関数の中で開き、`use` で閉じる。** `Resource` は使わない。connection は 1 つの関数の範囲で完結し、寿命を関数の外へ渡さない
5. **拒否も失敗も rollback する。** block が `Left`（業務上の拒否、競合）を返すとき、例外が投げられるとき、いずれも rollback する。`Right` のときだけ commit する

## 検討した案

| 案 | 判断 |
| :--- | :--- |
| 暗黙の環境：coroutine の context に `Connection` を載せる | 採らない。`transaction { }` の外から呼んでも compile は通り、実行時に初めて失敗する。transaction の中か外かが型に現れない。既存 Kotlin 版が 2 回目に踏んだ「文脈に接続を持たせた」ことと同じ種類の問題を、実行時の検査に移すだけになる |
| 空の文脈型（既存 Kotlin 版の `TxContext`） | 採らない。型引数 `Tx` なら、store ごとに中身を変えられ、キャストが要らない |
| `Resource` | 採らない。connection の寿命が 1 つの関数に収まるので、`use` と結果が同じ。第 9 章のスパイクで、成功しても例外でも閉じることは確認済み |
| 全体を `SERIALIZABLE` の transaction にする | 採らない。競合を database に任せるが、失敗を型で扱えず、どの command が失敗したかの情報が無い。版の確認（ADR-043）で足りる |

## 結果

- transaction の中か外かが型に現れます。ただし守られるのは、`Tx` の値を持たない場所（Hub と domain）から読み出しと追記を呼べない、という範囲です。`Tx` の値を持つ adapter の内側で、transaction の外へ持ち出して呼ぶことは、型では防げません。in-memory の `Tx` は `Unit` なので、この保護は働きません。
- `Transaction<Tx>` の戻り値は `Either<ZettaiError, List<ToDoListEvent>>` に固定しています。typealias では総称の関数型を表せないためです。command 以外の用途が現れたら、interface にして総称にします。
- query 側の `readAggregate`、`readUser` は、transaction の外で読みます（CQRS として分けています）。command 側の `readStream` は、版を返すために別に持ちます。
- `Tx` の型引数が port の型に広がります。波及の範囲は実装時に `git diff --stat` で数え、第 10 章に書きます。
- 組み立てた Hub は `Tx` を知らないので、web 層と Cucumber の経路には波及しません。
- `Resource` が必要になるのは、connection を複数の transaction にまたがって貸し出す場合（connection pool の自作など）です。その場合に見直します。

## 関連

- [ADR-011 トランザクションの境界をコマンド 1 つの処理に置き文脈を不透明な型にする](ADR-011-transaction-boundary.md)（既存 Kotlin 版）
- [ADR-043 楽観ロックを stream ごとの版と一意制約で行う](ADR-043-optimistic-locking-by-version.md)
- [ADR-040 JDBC を直接使い、blocking は Dispatchers.IO に閉じ込め、connection は use で閉じる](ADR-040-jdbc-blocking-boundary.md)
