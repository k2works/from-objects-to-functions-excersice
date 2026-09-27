---
type: ADR
title: "ADR-011 トランザクションの境界をコマンド 1 つの処理に置き文脈を不透明な型にする"
description: "Zettai 連載 Kotlin 版で、トランザクションの境界をコマンド 1 つの処理に置く決定と、ポートの文脈を Connection ではなく中身が空の TxContext にする決定。段階的に 2 回誤り、境界のテストが 2 つめを止めた経緯を記録する。"
tags: [adr, zettai, kotlin]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-27T02:59:39Z }
---

# ADR-011 トランザクションの境界をコマンド 1 つの処理に置き文脈を不透明な型にする

日付: 2026-09-27

## ステータス

2026-09-27 提案されました

## コンテキスト

第 9 章で `ContextReader` を作り、「`flatMap` が同じ文脈を両方に渡す」と書きました。第 10 章でそれをトランザクションとして使います。

決めることが 2 つあります。

1. **トランザクションの境界をどこに置くか**
2. **ポートの文脈の型を何にするか**

### 現状の問題

第 9 章の時点では、操作ごとに接続を作って閉じていました。

```text
append() → 接続を作る → INSERT → commit → 閉じる
append() → 接続を作る → INSERT → commit → 閉じる
```

2 つの操作が別のトランザクションになります。1 つめが成功して 2 つめが失敗すると、**1 つめだけが残ります。**

## 決定 1: 境界はコマンド 1 つの処理

トランザクションの単位は **コマンド 1 つの処理**とします。

```text
1 つのトランザクション = 状態の取得 + コマンドの判断 + 出来事の保存
```

理由は、**コマンドが業務上の 1 つの意図**だからです。「リストを作る」が半分だけ成功する状態に意味はありません。

境界を広げる案（HTTP リクエスト 1 つ）も考えましたが、採りませんでした。1 リクエストで複数のコマンドを処理する場面が今はなく、広げると「どこまでが 1 つの単位か」がコードから読み取りにくくなります。

実装は `runInTransaction` に閉じ込めます。

```kotlin
fun <T> HubAction<T>.runInTransaction(connect: () -> Connection): Outcome<ZettaiError, T>
```

**接続を作る場所が 1 箇所に集まります。** `autoCommit` を切り、成功なら commit、失敗なら rollback します。

## 決定 2: 文脈は中身が空の TxContext

ポート 3 つ（`StateFetcher`・`ProjectionFetcher`・`EventPersister`）の戻り値を、実行を後回しにする形に変えます。

```kotlin
typealias HubAction<T> = ContextReader<TxContext, T>
```

`TxContext` は**中身が空のインターフェース**です。

```kotlin
interface TxContext
```

ドメインは「文脈がある」ことだけを知り、それが何かを知りません。接続という具体は `zettai.persistence` の `JdbcContext` に閉じ込めます。

```kotlin
data class JdbcContext(val connection: Connection) : TxContext
```

`DbAction`（`ContextReader<Connection, T>`）を `HubAction` に持ち上げるときに取り出します。

```kotlin
fun <T> DbAction<T>.asHubAction(): HubAction<T> =
    ContextReader { context ->
        require(context is JdbcContext) { "この操作は接続を必要とするが、文脈が JdbcContext ではない: $context" }

        runWith(context.connection)
    }
```

インメモリの経路は `InMemoryContext` を渡します。文脈に触らないので何も起きません。

### この形に至るまでに 2 回誤った

[Unit 6 の Bolt 計画](../development/iteration_plan-6.md) に懸念を書いていました。

> インメモリの経路が「接続を使わない `DbAction`」を返すことになるのが、この変更のいちばん不自然な点です。接続が不要な実装に接続を受け取らせるのは、抽象が漏れている兆候かもしれません。

**懸念は当たり、しかも書いただけでは防げませんでした。**

| 試み | 結果 |
| :--- | :--- |
| 1. `HubAction<T> = ContextReader<Connection, T>` | インメモリの経路が `null as Connection` で **NPE**。Kotlin では非 null 型への null キャストは実行時に落ちる |
| 2. `TxContext` に `connection(): Connection` を持たせる | **`DomainBoundaryTest` が「ドメインが JDBC を import した」と検出** |
| 3. `TxContext` を中身が空にする | 成功 |

2 つめが重要です。**計画に懸念を書いていたのに、同じ種類の誤りを 1 段深いところで繰り返しました。** 止めたのは Unit 2 で作った境界のテストです。

## 影響

- **複数の操作がまとめて成功または失敗します。** 途中で失敗したら 1 件も残りません（結合テストで確認）
- **ポートの型変更の波及は 7 ファイル変更 + 3 ファイル新設**でした（第 7 章の `Outcome` 導入は 6 ファイル）
- **失敗の表現が変わりました。** 第 9 章では保存の失敗を `Outcome` で返していましたが、`HubAction` を返す形になったため、失敗は実行時（`runInTransaction`）に捕らえて `Outcome` に変えます。ポートの型からは失敗が見えなくなりました
- インメモリの経路で永続化の操作を混ぜると `require` で失敗します。型では防げず、実行時の検査になりました

### 最後の点について

**`TxContext` が空なので、型は「この操作が接続を必要とするか」を表せません。** インメモリの文脈に永続化の操作を渡すと実行時に落ちます。

型で防ぐには、文脈の種類ごとにポートを分ける（`InMemoryPort` と `JdbcPort`）ことになります。それはハブを 2 つ持つことに近く、CQRS とは別の分岐を持ち込みます。**実行時の検査で妥協しました。** この判断を第 10 章の記事に書きます。

## コンプライアンス

- `TxContext` が `zettai.domain` にあり、フレームワークを import しないこと（`DomainBoundaryTest`）
- 接続を作る場所が `runInTransaction` と `runOn` の 2 箇所に限られること
- 結合テストで「途中で失敗したら 1 件も残らない」ことを確かめること（SELECT で件数 0）

## 備考

- 起案: Unit 6 のステップ 6-1.3（[Unit 6 の Bolt 計画](../development/iteration_plan-6.md)）
- 関連: [ADR-006](ADR-006-outcome-port-type.md)（前回のポート型変更）、[ADR-009](ADR-009-integration-test-database.md)
