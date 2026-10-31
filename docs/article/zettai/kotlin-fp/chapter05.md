---
type: Article
title: "第 5 章 イベントで状態を変更する"
description: "Zettai Kotlin FP 版の第 5 章。immutable event list を再帰と fold で状態へ畳み込み、状態変換のモノイド則を Kotest property testing で検証する。"
tags: [article, zettai, kotlin, functional-programming, event, fold, monoid, property-testing]
status: draft
generated: { by: openai-codex/gpt-5, at: 2026-10-31T00:15:57Z }
---

# 第 5 章 イベントで状態を変更する

前章では、依存を小さな関数型として Hub へ渡しました。この章では、ToDo リストの変更そのものを関数で扱います。現在状態を破壊的に上書きせず、起きた出来事を immutable な列に残し、その列から状態を計算します。

## ToDo リストの作成の表示

最初の出来事は、リストが作られたことです。命令の `CreateList` ではなく、すでに起きた事実なので過去形の `ListCreated` と名付けます。

```kotlin
sealed interface ToDoListEvent {
    val user: User
    val listName: ListName
}
```

```kotlin
data class ListCreated(
    override val user: User,
    override val listName: ListName,
) : ToDoListEvent
```

項目の追加も出来事です。

```kotlin
data class ItemAdded(
    override val user: User,
    override val listName: ListName,
    val item: ToDoItem,
) : ToDoListEvent
```

イベントは data class なので、生成後に内容を変えません。`User` と `ListName` を共通契約に置き、`when` が全種類を列挙できる sealed interface にします。この時点では `ListCreated` と `ItemAdded` だけです。状態変更や削除を先回りして追加しません。

## 状態変更の保存

通常の CRUD は map の値を上書きします。簡単ですが、前の状態と「なぜ変わったか」を捨てます。ここでは、イベントを保存する対象とし、現在状態は必要なときに導出します。

導出先の状態も値です。

```kotlin
class ToDoListState private constructor(
    private val lists: Map<Pair<User, ListName>, ToDoList>,
) {
    fun listFor(user: User, listName: ListName): ToDoList? =
        lists[user to listName]

    companion object {
        val empty = ToDoListState(emptyMap())

        fun from(lists: Map<Pair<User, ListName>, ToDoList>): ToDoListState {
            val snapshot = lists
                .mapValues { (_, todoList) ->
                    todoList.copy(items = todoList.items.toList())
                }
                .toMap()

            return ToDoListState(snapshot)
        }
    }
}
```

constructor を private にし、外部の map から作るときは `from` が map と各項目列を copy します。状態は map そのものを公開しません。Kotlin の `Map` / `List` は read-only interface であって深い immutable collection ではないため、可変な別名を境界で切る必要があります。

1 つのイベントを適用する関数は、新しい map と list を返します。

```kotlin
fun ToDoListState.apply(event: ToDoListEvent): ToDoListState {
    return when (event) {
        is ListCreated -> put(
            event.user,
            event.listName,
            ToDoList(event.listName, emptyList()),
        )

        is ItemAdded -> {
            val current = listFor(event.user, event.listName) ?: return this
            put(
                event.user,
                event.listName,
                current.copy(items = current.items + event.item),
            )
        }
    }
}
```

`copy`、`Map.plus`、`List.plus` は元の値を書き換えません。テストでは `empty`、作成後、追加後の三つを同時に保持し、前二つが変化していないことを確認しました。

未作成のリストへ `ItemAdded` が来た場合は同じ状態を返します。重複した `ListCreated` はイベント順に同じ key のリストを空へ戻します。これは property generator が作る不正履歴も含めた機械的な規則で、通常操作から生成する正式な「再作成」機能ではありません。どちらも command で履歴の妥当性を検証する第 6 章より前の扱いです。イベント replay は、渡された出来事を決定的に適用することへ集中します。

## 再帰の力を解き放つ

1 件を適用できれば、列全体も適用できます。まず標準関数を使わず、再帰の形を露出させます。

```kotlin
fun List<ToDoListEvent>.replayByRecursion(from: ToDoListState): ToDoListState =
    when {
        isEmpty() -> from
        else -> drop(1).replayByRecursion(from.apply(first()))
    }
```

空なら初期状態を返します。空でなければ、先頭イベントを適用した状態と残りのイベントで同じ関数を呼びます。第 1 章のボウリングと同じ「終了条件」と「1 つ進めて残りを処理する」という形です。

この実装は学習用です。`drop(1)` は残りの list を作り、長い列では stack も使います。本番の replay として採用するのではなく、次の `fold` が何を隠しているかを目で確かめる足場にします。

テストは、作成と二つの項目追加からなる固定例に加え、Kotest が任意生成した初期状態と event list も再帰版と `fold` 版へ渡し、導出状態を比較します。空列は generator の範囲に必ず含め、未作成への追加、複数の利用者、重複作成という個別規則は固定テストで保証します。実装方法ではなく観測結果が同じことが契約です。

## イベントを畳み込む

再帰の定型を Kotlin 標準の `fold` へ置き換えます。

```kotlin
fun List<ToDoListEvent>.replayFrom(from: ToDoListState): ToDoListState =
    fold(from) { state, event -> state.apply(event) }
```

`fold` は初期値 `from` から始め、左から順に event を現在 state へ取り込みます。

```text
empty
  -> apply(ListCreated)
state 1
  -> apply(ItemAdded)
state 2
```

mutable な accumulator は見えません。各 step は古い状態を受け取り、新しい状態を返します。関数の外から見ると、同じ初期状態と event list は必ず同じ最終状態になります。

Kotlin FP 版でも、ここでは Arrow を使いません。標準の `fold` が列の畳み込みをすでに表しているからです。ライブラリを使うこと自体を目的にせず、自前で作る必要がなく、意図を短くできる抽象だけを選びます。

## モノイドの発見

イベントを別の角度から見ます。event を固定すると「状態を受け取り状態を返す関数」になります。

```kotlin
typealias StateTransition = (ToDoListState) -> ToDoListState
```

```kotlin
fun ToDoListEvent.asTransition(): StateTransition =
    { state -> state.apply(this) }
```

二つの状態変換は順番につなげられます。

```kotlin
infix fun StateTransition.andThen(next: StateTransition): StateTransition =
    { state -> next(this(state)) }
```

何もしない変換もあります。

```kotlin
val identityTransition: StateTransition = { it }
```

イベント列を変換へ畳み込むとき、この identity を初期値にします。

```kotlin
fun List<ToDoListEvent>.asTransition(): StateTransition =
    fold(identityTransition) { accumulated, event ->
        accumulated andThen event.asTransition()
    }
```

この構造には二つの重要な法則があります。

- 結合律: `(a andThen b) andThen c` と `a andThen (b andThen c)` の結果は同じです。
- 単位元: identity を左または右につないでも結果は変わりません。

状態変換、合成、単位元がそろい、法則が成り立つ構造をモノイドと呼びます。法則があると、長い event list を分割して別々に畳み込み、後で合成しても結果が変わらないと判断できます。

### 具体例ではなく property をテストする

一つの event list で成功しても、「任意の入力で成り立つ」証拠としては弱いままです。Kotlin FP 版では自前の乱数 helper を作らず、すでに依存へ含めた Kotest property testing へ生成と shrinking を委ねます。

生成器は `ListCreated` と `ItemAdded` の両方、および空を含む event list を作ります。

```kotlin
val events = Arb.list(event, 0..8)
```

結合律は三つの変換だけでなく、別に生成した event list から初期状態も作って検証します。

```kotlin
checkAll(events, events, events, events) { seed, a, b, c ->
    val initial = seed.replayFrom(ToDoListState.empty)
    val left = (a.asTransition() andThen b.asTransition()) andThen c.asTransition()
    val right = a.asTransition() andThen (b.asTransition() andThen c.asTransition())

    left(initial) shouldBe right(initial)
}
```

単位元は左と右を別々に確かめます。

```kotlin
(identityTransition andThen transition)(initial) shouldBe transition(initial)
(transition andThen identityTransition)(initial) shouldBe transition(initial)
```

さらに、イベント列を先に連結する方法と、別々に変換してから合成する方法を比較します。

```kotlin
(a + b).asTransition()(initial) shouldBe
    (a.asTransition() andThen b.asTransition())(initial)
```

関数は参照比較しません。生成した初期状態へ適用し、観測できる `ToDoListState` を比較します。property が失敗すれば Kotest は反例を縮小するため、自前 helper の生成回数や edge case 管理よりも、ドメインの法則に集中できます。

第 5 章のテストだけを素早く実行する場合は、Zettai の project root で次を使います。

Unix / Nix shell では次を実行します。

```text
bash ./gradlew :zettai-step3-domain-events:test --tests "zettai.domain.events.*"
```

Windows PowerShell では次を実行します。

```text
.\gradlew.bat :zettai-step3-domain-events:test --tests "zettai.domain.events.*"
```

## まとめ

- 状態を上書きせず、`ListCreated` と `ItemAdded` を immutable な出来事として表しました。
- イベント適用は元の状態を変えず、新しい state を返します。
- replay を再帰で書いて構造を見つけ、Kotlin 標準の `fold` へ置き換えました。
- event を `StateTransition` として見て、合成と identity を定義しました。
- 結合律、左右単位元、イベント列連結との対応を Kotest property testing で検証しました。
- Arrow を先回りして使わず、標準機能で十分な抽象は標準機能に委ねました。

次章では、利用者が何をしたいかをコマンドとして表します。コマンドからイベントを導出するとき、初めて `ToDoStatus` の遷移規則が必要になります。

## この章で書いたコード

- イベント: `apps/kotlin-fp/zettai/zettai-step3-domain-events/src/main/kotlin/zettai/domain/events/ToDoListEvent.kt`
- 状態: `apps/kotlin-fp/zettai/zettai-step3-domain-events/src/main/kotlin/zettai/domain/events/ToDoListState.kt`
- replay と合成: `apps/kotlin-fp/zettai/zettai-step3-domain-events/src/main/kotlin/zettai/domain/events/Replay.kt`
- property test: `apps/kotlin-fp/zettai/zettai-step3-domain-events/src/test/kotlin/zettai/domain/events/StateTransitionMonoidTest.kt`

## 参照

- Uberto Barbini『From Objects to Functions』第 5 章
- Michał Płachta『Grokking Functional Programming』Kotlin 版。immutable data、`fold`、property-based testing の使い分けを参照しました
- [Kotest property testing](https://kotest.io/docs/proptest/property-based-testing.html)

