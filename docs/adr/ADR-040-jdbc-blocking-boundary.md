---
type: ADR
title: "ADR-040 JDBC を直接使い、blocking は Dispatchers.IO に閉じ込め、connection は use で閉じる"
description: "Zettai 連載 Kotlin FP 版の第 9 章で永続化の道具を決める決定。ORM と JSON ライブラリを入れず、jsonb_build_object と bind parameter で payload を組み立てる。blocking の JDBC は adapter の Dispatchers.IO に閉じ込め、connection は use で閉じる。Arrow の Resource を使わない理由と、使うべき条件を記録する。"
tags: [adr, kotlin-fp, zettai, postgresql, jdbc, coroutines]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-10-31T06:11:51Z }
---

# ADR-040 JDBC を直接使い、blocking は Dispatchers.IO に閉じ込め、connection は use で閉じる

日付: 2026-10-31

## ステータス

2026-10-31 提案されました

## コンテキスト

第 9 章で event を PostgreSQL に追記します。保存するのは `todo_list_event` の 1 テーブルだけで、操作は追記と、集約またはユーザー単位の読み出しの 2 つです。決めることは 4 つあります。

1. ORM を使うか
2. payload の JSON をどう組み立てるか
3. blocking の JDBC を `suspend` の port の後ろでどう扱うか
4. connection の寿命を Arrow の `Resource` で表すか、`use` で足りるか

[ADR-039](ADR-039-suspend-function-ports.md) は、blocking の JDBC と `runBlocking` の関係を第 9 章のスパイクで決めるとしました。

### スパイクで測ったこと

使い捨てのテストで、Testcontainers の PostgreSQL 18 に対して次を試しました。

| 確認したこと | 結果 |
| :--- | :--- |
| `jsonb` は入れた文字列を保つか | 保たない。`{"b":1,   "a":[2, 3]}` は `{"a": [2, 3], "b": 1}` になる。空白が消え、キーが並べ替えられる |
| `jsonb_build_object` に bind parameter を渡す | 引用符、バックスラッシュ、改行を含む文字列と null が、手書きの escape なしで往復する。`payload->>'key'` で読むと JSON の null は SQL の NULL になる |
| `resourceScope` と `use` で connection は閉じるか | どちらも、正常終了でも例外でも閉じる |
| `withContext(Dispatchers.IO)` の中のスレッド | 呼び出し側とは別のワーカースレッドで動く |

## 決定

1. **ORM を使わず、JDBC を直接使う。** 操作は追記と読み出しだけで、ORM が解く問題がない。PostgreSQL の driver は version catalog にあり、新しい依存は増えない
2. **payload は `jsonb_build_object` と bind parameter で組み立て、`payload->>'key'` で読む。** JSON ライブラリを足さない。JSON の文字列を手で組み立てて文字列として比較すると、`jsonb` の正規化で崩れる。JSON を本格的に扱う第 12 章で、改めて道具を選ぶ
3. **blocking の JDBC は adapter の中で `withContext(Dispatchers.IO)` に閉じ込める。** port は `suspend` のままにし、domain と application を変えない。同期の port に戻す案は、第 4 章から使ってきた port の型をすべて書き換えるので採らない
4. **connection は操作ごとに開き、`use` で閉じる。** Arrow の `Resource` は使わない。上のとおり `use` と結果が同じで、この章の操作は 1 回の呼び出しで完結するため、寿命を持ち回る必要がない

`Resource` は、1 つの connection を複数の操作にまたがって貸す必要が出た時点で使う候補にします。第 10 章で transaction の境界を決めるときが、その時点です。

## 検討した案

| 案 | 判断 |
| :--- | :--- |
| Exposed などの ORM | 採らない。1 テーブルへの追記と読み出しに、ORM の抽象は大きすぎる |
| kotlinx.serialization などの JSON ライブラリ | 採らない。新しい依存は確認が必要で、SQL 側で payload を組み立てられるため要らない |
| 同期の port に戻す | 採らない。理由は上記 3 |
| 呼び出し元の thread で JDBC を直接呼ぶ | 採らない。`suspend` の port の後ろで thread をふさぐ呼び出しが隠れる |
| Arrow の `Resource` | 現時点では採らない。上記 4 |

## 結果

- 追記と読み出しに必要な道具は JDBC と SQL だけになります。event の種類ごとに、payload を組み立てる SQL と読む SQL を書く手間は増えます。
- blocking の呼び出しは adapter の 1 か所に集まり、`Dispatchers.IO` という名前で読めます。
- **ただし HTTP の thread は、まだふさがれます。** web の `runBlocking` が同期の http4k handler で結果を待つためです。`Dispatchers.IO` に出す利点は、blocking を adapter に隠し、非同期の server に替えたときに port をそのまま使えることです。同期の server のままでは、スレッドの総数は減りません。
- 読み出しは `entity_id` の前方一致で行うため、全件走査になる可能性があります。この連載の規模では問題にせず、性能の議論は扱いません。

## 関連

- [ADR-039 関数型 DI は suspend の関数型と constructor function にし、runBlocking は web の最外周に置く](ADR-039-suspend-function-ports.md)
- [ADR-008 イベントストアを 1 テーブルにする](ADR-008-event-store-single-table.md)（既存 Kotlin 版。スキーマの元）
- [データモデル設計](../design/data-model.md)
