---
type: Article
title: "第 5 章 イベントで状態を変更する"
description: "Zettai 連載 Kotlin 版の第 5 章。状態を上書きするのではなく出来事として残す設計に切り替える。リスト作成の表示を起点に、状態変更をイベントで保存し、再帰で畳み込んでから fold に置き換え、状態変換の合成が満たす法則を見つけて最後にモノイドという名前を与える過程を TDD で示す。"
tags: [article, zettai, kotlin, chapter]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-26T15:26:38Z }
---

# 第 5 章 イベントで状態を変更する

前章でハブを置き、アダプタを関数の型で受け取れるようにしました。この章では、いよいよ状態を変えます。

ただし、普通とは違うやり方で変えます。**上書きしません。**

## ToDo リストの作成の表示

新しいストーリーです。

> 利用者として、新しい ToDo リストを作りたい。なぜなら、用途ごとにリストを分けたいからだ。

受け入れテストに 1 つシナリオを足します。

```kotlin
    @TestFactory
    fun `作ったリストが表示される`() = ddtScenario {
        val uberto = ToDoListOwner("uberto")

        play(
            uberto.`creates a list`("shopping"),
            uberto.`sees an empty list`("shopping")
        )
    }
```

「リストを作る」「空のリストが見える」だけです。

さて、これをどう実装するか。ここで設計の分かれ道があります。

## 状態変更の保存

素直に考えると、こうなります。

<!-- code-check: ignore 比較のための擬似コード。本連載では採用しないため実装していない -->

```kotlin
class InMemoryStore {
    private val lists = mutableMapOf<Pair<User, ListName>, ToDoList>()

    fun createList(user: User, listName: ListName) {
        lists[user to listName] = ToDoList(listName, emptyList())
    }
}
```

動きます。しかし、この設計は**過去を捨てています**。

### 上書きで失われるもの

`lists[key] = newValue` を実行した瞬間、前の値は消えます。そして次のような問いに答えられなくなります。

- このリストはいつ作られたのか
- 項目はどの順で追加されたのか
- なぜ今この状態なのか
- 間違えて消したので、1 つ前に戻したい

「監査ログを別に取ればいい」と考えるかもしれません。しかしその場合、**ログと実際の状態がずれる**可能性が残ります。2 箇所に書くからです。

### 出来事だけを残す

別の設計があります。**状態を保存せず、起きたことだけを保存する。**

```kotlin
/**
 * ToDo リストに起きた出来事。
 *
 * 状態を上書きするのではなく、起きたことを並べて残す。
 * 現在の状態は、出来事を順に適用した結果として得られる。
 */
sealed interface ToDoListEvent {
    val user: User
    val listName: ListName
}

data class ListCreated(override val user: User, override val listName: ListName) : ToDoListEvent

data class ItemAdded(
    override val user: User,
    override val listName: ListName,
    val item: ToDoItem
) : ToDoListEvent
```

出来事は**過去形**で名付けます。`CreateList`（作れ）ではなく `ListCreated`（作られた）です。すでに起きたことなので、取り消せません。この名前の付け方が、設計の性質をそのまま表しています。

では、現在の状態はどこにあるのか。**計算します。**

## 再帰の力を解き放つ

出来事の列から状態を作ります。まず状態の型を決めます。

```kotlin
/** 出来事を適用した結果としての状態。 */
data class ToDoListState(val lists: Map<Pair<User, ListName>, ToDoList>) {

    fun listFor(user: User, listName: ListName): ToDoList? = lists[user to listName]

    companion object {
        val empty = ToDoListState(emptyMap())
    }
}
```

次に、出来事を 1 つ適用する関数です。

```kotlin
/** 1 つの出来事を状態に適用する。 */
fun ToDoListState.apply(event: ToDoListEvent): ToDoListState =
    when (event) {
        is ListCreated -> copy(lists = lists + ((event.user to event.listName) to ToDoList(event.listName, emptyList())))
        is ItemAdded -> {
            val key = event.user to event.listName
            val current = lists[key]
            if (current == null) this else copy(lists = lists + (key to current.copy(items = current.items + event.item)))
        }
    }
```

`copy` で新しい状態を作っています。元の状態は変わりません。**出来事を適用しても、前の状態は残る**ということです。

### 列に広げる

出来事が 1 つ適用できるなら、列も適用できます。再帰で書きます。

```kotlin
fun List<ToDoListEvent>.replayByRecursion(from: ToDoListState): ToDoListState =
    when {
        isEmpty() -> from
        else -> drop(1).replayByRecursion(from.apply(first()))
    }
```

「空なら今の状態。そうでなければ、最初の 1 つを適用して、残りを続ける」。

**第 1 章のボウリングと同じ形です。**

<!-- code-check: ignore 第 1 章の BowlingGame.kt からの抜粋（形の比較） -->

```kotlin
remainingFrames == 0 -> 0
rolls.isStrike() -> rolls.frameScore(rollsUsed = 1, bonusRolls = 2) + rolls.nextFrames(1, remainingFrames)
```

どちらも「終わりの条件」と「1 つ処理して残りを続ける」でできています。ループもカウンタもありません。

第 1 章ではボウリングの得点という、業務とは関係ない題材でこの形を練習しました。同じ形が、Zettai の中核に現れています。

## イベントを畳み込む

再帰は動きますが、Kotlin には同じことを表す標準の関数があります。

```kotlin
/** 再帰を fold に置き換えたもの。 */
fun List<ToDoListEvent>.replayFrom(from: ToDoListState): ToDoListState =
    fold(from) { state, event -> state.apply(event) }
```

1 行です。

### fold は何をしているのか

`fold` は「初期値」と「値を 1 つ取り込む関数」を受け取り、列を 1 つの値に**畳み込み**ます。

```text
fold(empty) { state, event -> state.apply(event) }

  empty
    ↓ apply(ListCreated)
  状態1
    ↓ apply(ItemAdded)
  状態2
    ↓ apply(ItemAdded)
  状態3
```

再帰で書いた `replayByRecursion` とまったく同じことをしています。テストで確かめました。

```kotlin
    @Test
    fun `再帰と fold は同じ結果になる`() {
        val events = listOf(
            ListCreated(user, listName),
            ItemAdded(user, listName, ToDoItem("write chapter"))
        )

        expectThat(events.replayByRecursion(ToDoListState.empty))
            .isEqualTo(events.replayFrom(ToDoListState.empty))
    }
```

どちらを使ってもいいのですが、`fold` には利点があります。**「何をしているか」が名前で分かる**ことです。`replayByRecursion` は読まないと分かりませんが、`fold` と書いてあれば「畳み込んでいる」と一目で分かります。

再帰を書いてから `fold` に置き換えたのは、**`fold` が何をしているかを先に自分で書いて確かめるため**です。いきなり `fold` から入ると、便利な関数を使っただけで終わります。

## モノイドの発見

ここから、この連載で最初の抽象概念が現れます。名前はまだ出しません。構造を先に見ます。

### 出来事を「変換」として見る

`apply` は「状態と出来事を受け取って状態を返す」関数でした。見方を変えます。**出来事を固定すると、「状態を受け取って状態を返す関数」になります。**

```kotlin
/**
 * 状態から状態への変換。
 *
 * 出来事 1 つひとつが、この変換になる。変換どうしは合成でき、
 * 合成しても変換であり続ける。
 */
typealias StateTransition = (ToDoListState) -> ToDoListState

/** 出来事を変換として見る。 */
fun ToDoListEvent.asTransition(): StateTransition = { state -> state.apply(this) }
```

`ListCreated` は「リストを 1 つ足す変換」、`ItemAdded` は「項目を 1 つ足す変換」です。

### 変換は繋げられる

変換どうしは繋げられます。

```kotlin
/** 2 つの変換を繋ぐ。合成の演算。 */
infix fun StateTransition.andThen(next: StateTransition): StateTransition = { next(this(it)) }
```

繋いだ結果も変換です。だから、さらに繋げます。

```kotlin
/** 出来事の列を 1 つの変換に畳み込む。 */
fun List<ToDoListEvent>.asTransition(): StateTransition =
    fold(identityTransition) { acc, event -> acc andThen event.asTransition() }
```

また `fold` が出てきました。今度は**状態ではなく変換を畳み込んでいます**。

### 何もしない変換

`fold` の初期値に `identityTransition` を使っています。

```kotlin
/** 何もしない変換。合成の単位元。 */
val identityTransition: StateTransition = { it }
```

受け取った状態をそのまま返します。出来事が 1 つも無いときの答えです。

### 2 つの性質

ここまでの部品には、2 つの性質があります。

**性質 1: 繋ぐ順序をどう括っても結果が同じ。**

```text
(a と b を繋いだもの) と c を繋ぐ
a と (b と c を繋いだもの) を繋ぐ
```

この 2 つは同じ結果になります。

**性質 2: 何もしない変換を繋いでも何も変わらない。**

```text
何もしない変換 → f    は   f と同じ
f → 何もしない変換    は   f と同じ
```

当たり前に見えるかもしれません。しかし、**当たり前が成り立つことを確かめておくと、安心して組み替えられます**。たとえば「出来事の列を 2 つに分けて別々に畳み込み、最後に合わせる」という最適化が、結果を変えないと保証されます。

### 例では足りない

この 2 つの性質を、テストでどう確かめるか。

例を 3 つ書いても足りません。**「どんな入力でも成り立つ」と言いたいのに、確かめたのは 3 つだけ**だからです。反例は 4 つ目にあるかもしれません。

そこで、ランダムな入力を多数試します。

```kotlin
    @Test
    fun `結合律が成り立つ`() {
        repeatWithRandomEvents { a, b, c ->
            val left = (a.asTransition() andThen b.asTransition()) andThen c.asTransition()
            val right = a.asTransition() andThen (b.asTransition() andThen c.asTransition())

            expectThat(left(ToDoListState.empty)).isEqualTo(right(ToDoListState.empty))
        }
    }
```

`repeatWithRandomEvents` は、ランダムな出来事の列を 3 つ作って 200 回試します。

```kotlin
    private fun repeatWithRandomEvents(
        check: (List<ToDoListEvent>, List<ToDoListEvent>, List<ToDoListEvent>) -> Unit
    ) {
        repeat(trials) { seed ->
            val random = Random(seed)

            check(
                EventGenerator.events(random, random.nextInt(0, 5)),
                EventGenerator.events(random, random.nextInt(0, 5)),
                EventGenerator.events(random, random.nextInt(0, 5))
            )
        }
    }
```

シードを固定しているので、失敗したら同じ入力を再現できます。

このように「具体例ではなく性質を確かめる」テストを**プロパティベーステスト**と呼びます。ライブラリもありますが、本連載では 20 行の自前で済ませています。理由は [ADR-005](../../../adr/ADR-005-property-based-testing.md) に書きました。

### 名前を与える

さて、ここまで見つけた構造を整理します。

1. ある型の値がある（`StateTransition`）
2. 2 つを繋ぐ演算がある（`andThen`）
3. 繋ぐ順序をどう括っても結果が同じ（結合律）
4. 繋いでも何も変わらない特別な値がある（単位元 `identityTransition`）

**この 4 つを満たす構造を、モノイドと呼びます。**

名前を後に回したのは、順序が大事だからです。「モノイドとは結合律と単位元を持つ代数構造である」から始めると、定義は正しくても、なぜそれが嬉しいのか分かりません。**先に構造を見つけて、後から名前を知るほうが、記憶に残ります。**

### モノイドは既に身の回りにある

同じ構造は他にもあります。

| 型 | 演算 | 単位元 |
| :--- | :--- | :--- |
| 整数 | 足し算 | 0 |
| 整数 | 掛け算 | 1 |
| 文字列 | 連結 | 空文字列 |
| リスト | 連結 | 空リスト |
| 状態変換 | 合成 | 何もしない変換 |

最後の行が、この章で見つけたものです。他の行は誰でも知っています。**知っていたことに名前が付いた**だけです。

そして「リストの連結」と「状態変換の合成」が同じ構造なので、両者は対応します。確かめました。

```kotlin
    @Test
    fun `列の連結と変換の合成は同じ結果になる`() {
        repeatWithRandomEvents { a, b, _ ->
            val concatenated = (a + b).asTransition()
            val composed = a.asTransition() andThen b.asTransition()

            expectThat(concatenated(ToDoListState.empty)).isEqualTo(composed(ToDoListState.empty))
        }
    }
```

出来事の列を繋いでから変換にしても、別々に変換にしてから繋いでも、結果は同じです。**だから、出来事を溜めておいて後でまとめて処理しても、1 つずつ処理しても、同じ状態になります。** 実装を選ぶ自由が生まれます。

## まとめ

この章でやったことを振り返ります。

- **状態を上書きせず、出来事として残しました。** 過去が失われないので、「なぜ今この状態なのか」に答えられます
- **出来事の列から状態を計算しました。** まず再帰で書き、`fold` に置き換えました。第 1 章のボウリングと同じ形が、Zettai の中核に現れました
- **出来事を「状態の変換」として見ました。** 変換は合成でき、合成しても変換であり続けます
- **その構造がモノイドであることを確かめました。** 結合律と単位元を、ランダムな入力 200 回で検証しました。名前は最後に与えました

この章で扱ったイベントには、まだ足りないものがあります。**誰がそれを起こすのか**です。今は受け入れテストが直接イベントを作っていますが、本来は利用者の操作から生まれるはずです。

次の章では、利用者の意図を**コマンド**として表し、コマンドからイベントを生成する仕組みを作ります。そこで `status` を使った状態遷移も扱います。

---

## この章で書いたコード

- イベント: `apps/kotlin/zettai/zettai-step2-domain/src/main/kotlin/zettai/domain/events/ToDoListEvent.kt`
- 状態: `.../events/ToDoListState.kt`
- 畳み込みと合成: `.../events/Replay.kt`
- テスト: `.../test/kotlin/zettai/domain/events/`（`ToDoListEventTest.kt`・`StateTransitionMonoidTest.kt`・`EventGenerator.kt`）

## 参照

- Uberto Barbini『From Objects to Functions』第 5 章。イベントの畳み込みからモノイドを見出す流れは本書に拠っています。本連載のコードはすべて書き起こした自作実装です
