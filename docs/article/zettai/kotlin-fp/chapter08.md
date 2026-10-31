---
type: Article
title: "第 8 章 ファンクタを使ってイベントを射影する"
description: "Zettai Kotlin FP 版の第 8 章。ドメインの状態と画面が欲しい形が違うという困りごとから、イベントを表示用の行へ射影する。Either と List を重ねた map が満たす法則を Kotest property testing で確かめ、command 側と query 側を別の経路に分けて CQRS に到達する。"
tags: [article, zettai, kotlin, functional-programming, arrow, projection, cqrs, functor]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-10-31T06:37:42Z }
---

# 第 8 章 ファンクタを使ってイベントを射影する

前章で失敗を型に載せました。この章では、表示のしかたを見直します。

きっかけは困りごとです。**ドメインの状態と、画面が欲しい形が違う**のです。

## イベントを射影する

第 5 章から、状態を `ToDoListState` という 1 つの型で持ってきました。command を判断するとき、この形は都合がいいです。「同名のリストがすでにあるか」「その項目は今どの状態か」を判断する情報が揃っています。

### 画面が欲しいものは違う

利用者に「リストの一覧」を見せたいとします。画面に出したいのは、たとえばこうです。

| リスト名 | 項目数 | 完了 |
| :--- | :--- | :--- |
| book | 3 | 1 |
| shop | 5 | 5 |

`ToDoListState` から作れます。ただし画面ごとに状態から読み替えるコードを書くと、同じような変換が散らばります。そして**状態を持つ理由が 2 つ混ざります**。

| 状態を持つ理由 | 必要な形 |
| :--- | :--- |
| 業務ルールを判断する（command 側） | 判断に必要な情報が揃っている形 |
| 画面に見せる（query 側） | 表示したい形 |

1 つの型で両方を満たそうとすると、どちらにも最適でない形になります。

### 表示用のモデルを別に作る

表示のためのモデルを別に持ちます。

```kotlin
data class ToDoListRow(
    val listName: ListName,
    val itemCount: Int,
    val doneCount: Int,
)
```

この行をどこから作るか。**イベントから作ります。**

### 第 5 章と同じ形

第 5 章の `replayFrom` を思い出してください。

```kotlin
fun List<ToDoListEvent>.replayFrom(
    initial: ToDoListState = ToDoListState.empty,
): ToDoListState =
    fold(initial) { state, event -> state.apply(event) }
```

射影も同じ `fold` です。

```kotlin
fun projectFrom(user: User, events: List<ToDoListEvent>): List<ToDoListRow> =
    events
        .filter { it.user == user }
        .fold(emptyMap<ListName, List<ToDoItem>>()) { lists, event -> lists.step(event) }
        .map { (listName, items) ->
            ToDoListRow(
                listName = listName,
                itemCount = items.size,
                doneCount = items.count { it.status == ToDoStatus.Done },
            )
        }
```

**違うのは畳み込む先だけです。** 同じイベントの列を、状態ではなく行へ畳み込みます。状態を上書きしていたら、あとから別の形が欲しくなったときに作り直せません。イベントが残っているので、いくつでも射影を作れます。

### 射影を実装する

1 つの event を適用する部分です。

```kotlin
private fun Map<ListName, List<ToDoItem>>.step(event: ToDoListEvent): Map<ListName, List<ToDoItem>> =
    when (event) {
        is ListCreated -> this + (event.listName to emptyList())

        is ItemAdded -> {
            val items = this[event.listName]
            if (items == null) this else this + (event.listName to (items + event.item))
        }
```

未作成のリストへの `ItemAdded` は無視します。`ItemStatusChanged` も、説明が最初に一致した項目だけを変えます。

```kotlin
        is ItemStatusChanged -> {
            val updated = this[event.listName]?.withStatusChanged(event.description, event.newStatus)
            if (updated == null) this else this + (event.listName to updated)
        }
    }
```

「説明が最初に一致した項目だけを変える」規則は、状態の `apply` と射影の両方に必要です。同じ規則を 2 か所に書くと食い違うので、小さな関数に抜き出して共有しました。

```kotlin
fun List<ToDoItem>.withStatusChanged(description: String, newStatus: ToDoStatus): List<ToDoItem>? {
    val target = indexOfFirst { it.description == description }
    if (target < 0) return null
    return mapIndexed { index, item -> if (index == target) item.copy(status = newStatus) else item }
}
```

それでも畳み込みの骨格は、状態と射影で 2 か所に残ります。2 つが一致することを、後で property test にします。なお、`ToDoListState.apply` は Kotlin 標準のスコープ関数 `apply` と同じ名前です。第 5 章のコード例と揃えるため、改名していません。

### 既存 Kotlin 版との違い

| | 既存 Kotlin 版 | Kotlin FP 版 |
| :--- | :--- | :--- |
| 表示用の型 | `ToDoListProjection` というクラス。行の Map と項目の Map を持つ | `ToDoListRow` と関数 `projectFrom` だけ。クラスを増やさない |
| 行の並び | リスト名の昇順 | リストの作成順 |
| 集計のタイミング | event を適用するたびに集計し直す | 畳み込みの最後に数える |
| 同じ説明の項目が複数あるとき | 説明が一致する項目をすべて変える | 最初に一致した 1 件だけを変える。状態の `apply` と同じ規則 |

FP 版は、集計を畳み込みの最後に 1 回だけ行います。そのぶん途中の状態に集計値を持たず、`Map` と `List` だけで済みます。

## ファンクタに対するクエリの実行

射影した結果を、画面向けの別の値に変換することを考えます。取り出した行に `map` をかけます。

この章の時点では、読み出しは失敗しません。第 9 章でデータベースから読むようになると、読み出しが失敗しうるようになり、query の結果は `Either<ZettaiError, List<ToDoListRow>>` になります。第 7 章の `Either` の中に `List` が入る形です。その二重の文脈でも `map` の法則が保たれることを、先に確かめておきます。

```kotlin
    val results: Arb<Either<ZettaiError, List<ToDoListRow>>> = Arb.choice(
        arbEvents.map { projectFrom(genUsers.first(), it).right() },
        arbError.map { it.left() },
    )
```

`Either.map` で中の `List` を取り出し、さらに `List.map` で各行を変換します。`map` が二重になります。

### 同じ法則が成り立つ

第 7 章で確かめた恒等則と合成則を、この二重の `map` で確かめます。

```kotlin
    "クエリ結果の map は恒等則を満たす" {
        checkAll(results) { result ->
            result.map { it } shouldBe result
        }
    }
```

```kotlin
    "クエリ結果の map は合成則を満たす" {
        checkAll(results, rowToInt, intToText) { result, f, g ->
            result.map { rows -> rows.map(f) }.map { values -> values.map(g) } shouldBe
                result.map { rows -> rows.map { g(f(it)) } }
        }
    }
```

`arbEvents`（イベント列）と `arbError`（error）は、テスト全体で共有する生成器です。関数 `f` と `g` も生成します。失敗がそのまま流れることは、第 7 章で確かめました。ここでは、`Either` と `List` を重ねても法則が保たれることを確かめます。

```kotlin
    val rowToInt: Arb<(ToDoListRow) -> Int> =
        Arb.int(-5..5).map { k -> { row: ToDoListRow -> row.itemCount * k + row.doneCount } }
    val intToText: Arb<(Int) -> String> =
        Arb.string(0..3).map { prefix -> { n: Int -> "$prefix$n" } }
```

## ファンクタの観点から考察する

ここまでで、`map` が出てくる型が並びました。

| 型 | `map` の意味 | 章 |
| :--- | :--- | :--- |
| `List<T>` | 各要素に適用する | 標準ライブラリ |
| `Either<E, A>` | 成功なら適用し、失敗はそのまま流す | 7 |
| `Either<E, List<A>>` | 成功なら各要素に適用する | 8 |

どれも恒等則と合成則を満たします。ファンクタを 2 つ重ねても、法則は保たれます。重ねた `map` を 1 回にまとめられるのは、この法則があるからです。

既存 Kotlin 版は、`ToDoListProjection` に自作の `map` を持たせ、その法則を確かめました。FP 版では、ファンクタ自体を作りません。標準の `List.map` と Arrow の `Either.map` に委ね、確かめるのは「重ねても法則が保たれる」ことと、「射影の結果が replay した状態と一致する」ことです。

一致は次の property test で確かめます。`fromState` は、replay した状態から行を数え直したものです。

```kotlin
    "射影の行は、replay した状態から数えた行と一致する" {
        checkAll(arbEvents) { events ->
            val state = events.replayFrom()

            genUsers.forEach { user ->
                val fromState = genListNames
                    .mapNotNull { name ->
                        state.listFor(user, name)?.let { list ->
                            ToDoListRow(
                                listName = name,
                                itemCount = list.items.size,
                                doneCount = list.items.count { it.status == ToDoStatus.Done },
                            )
                        }
                    }
                    .associateBy { it.listName }

                projectFrom(user, events).associateBy { it.listName } shouldBe fromState
            }
        }
    }
```

任意のイベント列で、command 側の状態から数えた行と、query 側の射影が一致します。ここに「未作成のリストへの event」や「複数のユーザー」が混ざるように生成器を作っています。

## コマンドクエリ責務分離(CQRS)

射影ができたので、Hub を整理します。

### 経路を分ける

ここで集約とは、`User` と `ListName` の組で決まる 1 つのリストを、判断の単位として呼ぶ名前です。port は、何を読むかを名前で示します。この章の時点では、読み出しは失敗しない `List` を返します。第 9 章で永続化を入れるとき、戻り値を `Either` に変えます。

<!-- code-check: ignore 第 9 章で戻り値を Either にする前の形 -->

```kotlin
/** command 側が読む。判断に必要な集約のイベントだけを返す。 */
typealias AggregateEventReader = suspend (User, ListName) -> List<ToDoListEvent>

/** query 側が読む。ユーザーのイベントを返し、射影の入力になる。 */
typealias UserEventReader = suspend (User) -> List<ToDoListEvent>
```

第 6 章までの `StateFetcher` は、全ユーザー・全リストの状態を毎回 replay して返していました。command は `user` と `listName` を必ず持つので、その集約のイベントだけで判断できます。読む単位を絞ると、第 9 章でデータベースの `entity_id` で絞る形にそのまま対応します。

Hub は、command 側と query 側を別の関数として持ちます。

```kotlin
data class ToDoListHub(
    val getList: ListQuery,
    val listRows: RowsQuery,
    val execute: CommandExecutor,
)
```

`ListQuery`、`RowsQuery`、`CommandExecutor` は、それぞれの関数型に付けた名前です。

<!-- code-check: ignore 第 9 章で RowsQuery の戻り値を Either にする前の形 -->

```kotlin
typealias ListQuery = suspend (User, ListName) -> Either<ZettaiError, ToDoList>

typealias RowsQuery = suspend (User) -> List<ToDoListRow>

typealias CommandExecutor = suspend (ToDoListCommand) -> Either<ZettaiError, List<ToDoListEvent>>
```

<!-- code-check: ignore 第 9 章で Either を返す port に合わせる前の形 -->

```kotlin
fun todoListHub(
    readAggregate: AggregateEventReader,
    readUser: UserEventReader,
    persist: EventPersister,
): ToDoListHub =
    ToDoListHub(
        getList = { user, listName ->
            readAggregate(user, listName)
                .replayFrom()
                .listFor(user, listName)
                ?.right()
                ?: ListNotFound(user, listName).left()
        },
        listRows = { user -> projectFrom(user, readUser(user)) },
        execute = { command ->
            either {
                val state = readAggregate(command.user, command.listName).replayFrom()
                val events = handle(command, state).bind()
                persist(events)
                events
            }
        },
    )
```

`execute` は第 6 章と同じく、検証に成功した event だけを保存します。`getList` と `listRows` は、保存しません。

### これが CQRS

**command（変更）と query（参照）で別のモデルを使う。** それが CQRS（Command Query Responsibility Segregation）です。名前は最後に出しました。困りごとから出発して構造を作り、あとで名前を知る順序は、モノイドとファンクタのときと同じです。

| | command 側 | query 側 |
| :--- | :--- | :--- |
| 読むもの | 集約のイベント | ユーザーのイベント |
| 作るもの | `ToDoListState` | `ToDoListRow` |
| 目的 | 業務ルールを判断する | 表示に答える |
| 書き込み | する（event を保存） | しない |

片方を変えても、もう片方には影響しません。新しい画面が必要になれば射影を足すだけで、command 側は変わりません。

### CQRS の代償

正直に書きます。

| 失うもの | 内容 |
| :--- | :--- |
| コード量 | 状態と射影で 2 つのモデルを持つ。同じ規則を 2 か所に書く |
| 理解のコスト | 「どちらを見るのか」を毎回考える |
| 一貫性 | 射影を保存して使うと、古い値が見える可能性がある。この連載は毎回 event から射影するので起きない |

規則が 2 か所にあることの代償は、前節の property test が払います。画面が 1 つなら CQRS は過剰です。Zettai は、この設計を説明するための題材として採用しています。

### 役割を終えたものを消す

query 側が event から導くようになったので、`ToDoList` を直接返す fetcher は要らなくなりました。`ToDoListFetcher` と in-memory の fetcher を削除しています。**event store が、command と query のただ 1 つの源です。**

この章では一覧画面の route を追加していません。`listRows` は Hub の query として検証します。

## まとめ

- 困りごとから出発しました。ドメインの状態と画面が欲しい形は違い、1 つの型で両方を満たすとどちらにも最適でなくなります。
- 表示用の `ToDoListRow` を、イベントからの射影として作りました。`replayFrom` と同じ `fold` で、違うのは畳み込む先だけです。
- `Either` と `List` を重ねた `map` が、恒等則と合成則を満たすことを property test で確かめました。ファンクタを自作せず、標準と Arrow の `map` に委ねました。
- 射影と replay の規則が一致することを、任意のイベント列で確かめました。
- command 側を集約単位、query 側をユーザー単位で読む port に分け、Hub が別の経路を持つようにしました。これが CQRS です。
- 代償も書きました。モデルが 2 つになり、規則が 2 か所になります。

次の章では、そのイベントをデータベースに保存します。ここまでメモリの中にあったので、再起動すると消えていました。

## この章で書いたコード

- 射影: `apps/kotlin-fp/zettai/zettai-step5-cqrs-persistence/src/main/kotlin/zettai/domain/queries/Projection.kt`
- Hub: `apps/kotlin-fp/zettai/zettai-step5-cqrs-persistence/src/main/kotlin/zettai/application/ToDoListHub.kt`
- in-memory の event store: `apps/kotlin-fp/zettai/zettai-step5-cqrs-persistence/src/main/kotlin/zettai/adapter/inmemory/InMemoryAdapters.kt`
- 射影のテスト: `apps/kotlin-fp/zettai/zettai-step5-cqrs-persistence/src/test/kotlin/zettai/domain/queries/ProjectionTest.kt`
- 法則のテスト: `apps/kotlin-fp/zettai/zettai-step5-cqrs-persistence/src/test/kotlin/zettai/domain/queries/ProjectionLawsTest.kt`

この章から `zettai-step5-cqrs-persistence` module に移ります。第 3 章の Cucumber の scenario も、この module に移しました。前提は command で用意し、domain と HTTP の 2 経路で同じ scenario を確かめます。第 9 章で 3 経路目を足します。コードの一部は第 9 章で変わるため、ここに挙げるファイルは第 9 章の変更を含む最新の形です。

この module には、第 9 章で足す PostgreSQL の結合テストも含まれます。module 全体のテストは Docker daemon を必要とします。Docker を使わずにこの章の範囲を確かめるには、`apps/kotlin-fp/zettai/` で次のように絞り込みます。

Unix / Nix shell:

```text
bash ./gradlew :zettai-step5-cqrs-persistence:test --tests "zettai.domain.*" --tests "zettai.application.ToDoListHubTest" --tests "zettai.web.*" --tests "zettai.adapter.inmemory.*" --tests "zettai.smoke.*"
```

Windows PowerShell:

```text
.\gradlew.bat :zettai-step5-cqrs-persistence:test --tests "zettai.domain.*" --tests "zettai.application.ToDoListHubTest" --tests "zettai.web.*" --tests "zettai.adapter.inmemory.*" --tests "zettai.smoke.*"
```

## 参照

- Uberto Barbini『From Objects to Functions』第 8 章
- Michał Płachta『Grokking Functional Programming』Kotlin 版。`map` とファンクタ、immutable data の説明を参照しました
- [Arrow Either](https://arrow-kt.io/learn/typed-errors/either-and-ior/)
- [Kotest property testing](https://kotest.io/docs/proptest/property-based-testing.html)
