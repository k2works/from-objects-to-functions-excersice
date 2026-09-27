---
type: Article
title: "第 8 章 ファンクタを使ってイベントを射影する"
description: "Zettai 連載 Kotlin 版の第 8 章。ドメインの状態と表示に必要な形が違うという困りごとから出発し、イベントを表示用のモデルへ射影する。射影の map がファンクタであることを確かめ、コマンド側とクエリ側を別の経路に分けて CQRS に到達する過程を TDD で示す。"
tags: [article, zettai, kotlin, chapter]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-27T01:49:55Z }
---

# 第 8 章 ファンクタを使ってイベントを射影する

前章で失敗を型に載せました。この章では、表示のしかたを見直します。

きっかけは困りごとです。**ドメインの状態と、画面が欲しい形が違う**のです。

## イベントを射影する

第 5 章から、状態を 1 つの型で持ってきました。

<!-- code-check: ignore 第 5 章時点のコード。この章で別の畳み込み先を足す -->

```kotlin
data class ToDoListState(val lists: Map<Pair<User, ListName>, ToDoList>) {
    fun listFor(user: User, listName: ListName): ToDoList?
}
```

コマンドを処理するとき、この形は都合がいいです。「同名のリストが既にあるか」「その項目は今どの状態か」を判断するのに必要な情報が揃っています。

### 画面が欲しいものは違う

利用者に「リストの一覧」を見せたいとしましょう。画面に出したいのは、たとえばこうです。

| リスト名 | 項目数 | 完了 |
| :--- | :--- | :--- |
| book | 3 | 1 |
| shopping | 5 | 5 |

`ToDoListState` から作れます。リストを全部取り出して、項目を数えて、完了した数を数える。

**しかしこれを画面ごとに書くと、同じような読み替えが散らばります。** 一覧画面、詳細画面、集計画面、それぞれが `ToDoListState` の形から自分の欲しい形へ変換するコードを持つことになります。

そして困るのが、**状態を持つ理由が 2 つ混ざる**ことです。

| 状態を持つ理由 | 必要な形 |
| :--- | :--- |
| 業務ルールを判断する（コマンド側） | 判断に必要な情報が揃っている形 |
| 画面に見せる（クエリ側） | 表示したい形 |

1 つの型で両方を満たそうとすると、どちらにも最適でない形になります。

### 表示用のモデルを別に作る

そこで、表示のためのモデルを**別に持ちます**。

```kotlin
/**
 * 表示のためのモデル。
 *
 * ドメインの状態（ToDoListState）とは別に持つ。画面が欲しい形は、
 * 業務ルールを判断するために必要な形とは違う。
 */
data class ToDoListRow(
    val listName: ListName,
    val itemCount: Int,
    val doneCount: Int
)
```

では、この形をどこから作るか。**イベントから作ります。**

```kotlin
/**
 * イベントから表示用のモデルを作る射影。
 *
 * 第 5 章の畳み込みと同じ形。畳み込む先が状態ではなく表示用のモデルになっただけ。
 */
data class ToDoListProjection(
    private val rows: Map<Pair<User, ListName>, ToDoListRow>,
    private val items: Map<Pair<User, ListName>, List<ToDoItem>>
) {
```

これを **射影（projection）** と呼びます。同じイベントの列から、別の形を作ることです。

### 第 5 章と同じ形

畳み込みは第 5 章とまったく同じです。

```kotlin
/** 出来事の列を射影に畳み込む。 */
fun List<ToDoListEvent>.projectFrom(from: ToDoListProjection): ToDoListProjection =
    fold(from) { projection, event -> projection.project(event) }
```

第 5 章の `replayFrom` と見比べてください。

<!-- code-check: ignore 第 5 章の Replay.kt からの抜粋（形の比較） -->

```kotlin
fun List<ToDoListEvent>.replayFrom(from: ToDoListState): ToDoListState =
    fold(from) { state, event -> state.apply(event) }
```

**違うのは畳み込む先だけです。** 同じイベントの列を、違う形に畳み込んでいます。

これがイベントを残す設計の見返りです。状態を上書きしていたら、「別の形も欲しい」と言われたときに作り直せません。**イベントが残っていれば、後からいくつでも射影を作れます。**

### 射影を実装する

出来事を 1 つ適用する部分です。

```kotlin
    /** 1 つの出来事を適用する。 */
    fun project(event: ToDoListEvent): ToDoListProjection {
        val key = event.user to event.listName

        return when (event) {
            is ListCreated -> copy(
                rows = rows + (key to ToDoListRow(event.listName, itemCount = 0, doneCount = 0)),
                items = items + (key to emptyList())
            )

            is ItemAdded -> withItems(key, (items[key] ?: return this) + event.item)

            is ItemStatusChanged -> {
                val current = items[key] ?: return this

                withItems(key, current.map { it.withStatusIfMatches(event) })
            }
        }
    }
```

項目が変わったら、集計し直します。

```kotlin
private fun summarize(listName: ListName, items: List<ToDoItem>): ToDoListRow =
    ToDoListRow(
        listName = listName,
        itemCount = items.size,
        doneCount = items.count { it.status == ToDoStatus.Done }
    )
```

**集計を「画面に出すとき」ではなく「イベントを適用するとき」にやっています。** 表示のたびに数え直しません。

## ファンクタに対するクエリの実行

射影から値を取り出す操作を見ます。

```kotlin
    fun listsFor(user: User): List<ToDoListRow> =
        rows.filterKeys { it.first == user }.values.sortedBy { it.listName.name }

    fun itemsFor(user: User, listName: ListName): List<ToDoItem>? = items[user to listName]
```

そして、射影全体を別の形に変換する操作もあります。

```kotlin
    /** 射影を別の形に変換する。ファンクタとして振る舞う。 */
    fun <T> map(f: (ToDoListRow) -> T): List<T> = rows.values.sortedBy { it.listName.name }.map(f)
```

`map` です。第 7 章の `Outcome.map` と同じ名前です。

### 同じ法則が成り立つ

第 7 章で、`map` が満たす 2 つの法則を確かめました。恒等則と合成則です。

射影の `map` でも同じ法則が成り立ちます。

```kotlin
    @Test
    fun `合成則が成り立つ`() {
        forAllRandom { random ->
            val projection = randomProjection(random)

            expectThat(projection.map(f).map(g)).isEqualTo(projection.map { g(f(it)) })
        }
    }
```

テストの形も第 7 章と同じです。`forAllRandom` は第 5 章から使っているヘルパーで、ランダムな入力を 200 回試します。

生成器は、イベントの生成器（第 5 章）を使い回しています。

```kotlin
    private fun randomProjection(random: kotlin.random.Random): ToDoListProjection =
        EventGenerator.events(random, random.nextInt(0, 8)).projectFrom(ToDoListProjection.empty)
```

**ランダムなイベントの列を作り、射影に畳み込む。** 第 5 章で作った道具が、そのまま使えています。

## ファンクタの観点から考察する

ここまでで `map` が 3 つ出てきました。

| 型 | map の意味 | 章 |
| :--- | :--- | :--- |
| `List<T>` | 各要素に適用する | （標準ライブラリ） |
| `Outcome<E, T>` | 成功なら適用する | 7 |
| `ToDoListProjection` | 各行に適用する | 8 |

どれも同じ 2 つの法則を満たします。**「`map` がある」ことと「法則を満たす」ことが揃うと、ファンクタと呼べます。**

### 法則を知っていると何が得か

法則が成り立つと保証されていると、**安心して組み替えられます**。

- `map` を 2 回呼んでいるところを 1 回にまとめる → 結果は変わらない（合成則）
- `map { it }` を削る → 結果は変わらない（恒等則）

そして**読むときも楽になります**。`map` と書いてあれば、「中身に何かしているだけで、構造は変えていない」と分かります。要素が減ったり増えたりしないことが名前で保証されます。

逆に言えば、**法則を満たさない `map` を書いてはいけません**。たとえば `map` の中で条件によって要素を捨てると、合成則が壊れます。そういう操作には `filter` という別の名前があります。

### この連載での位置づけ

第 5 章でモノイド、第 7 章でファンクタ、そしてこの章でもファンクタが出てきました。第 9 章ではモナドが出ます。

**同じ道具（法則をプロパティベーステストで確かめる）が使い回せています。** 新しい概念が出るたびに新しいテストの書き方を覚える必要はありません。

## コマンドクエリ責務分離(CQRS)

射影ができたので、ハブを整理します。

### 経路を分ける

```kotlin
/**
 * ドメインの入口。
 *
 * コマンド側とクエリ側で別の経路を持つ（CQRS）。
 * コマンド側は状態を見て業務ルールを判断し、クエリ側は射影を見て表示に答える。
 */
class ToDoListHub(
    private val fetchState: StateFetcher,
    private val fetchProjection: ProjectionFetcher,
    private val persist: EventPersister
) {
```

アダプタが 3 つになりました。`fetchState` はコマンド側が、`fetchProjection` はクエリ側が使います。

```kotlin
    // --- クエリ側 ---

    fun itemsFor(user: User, listName: ListName): Outcome<ZettaiError, List<ToDoItem>> =
        fetchProjection().itemsFor(user, listName)?.asSuccess()
            ?: ListNotFound("${listName.name} が見つかりません").asFailure()
```

コメントで区切っているのは、**読む人に「ここから別の話」と伝えるため**です。

### これが CQRS

**コマンド（変更）とクエリ（参照）で別のモデルを使う。** それが CQRS（Command Query Responsibility Segregation）です。

パターンの名前は最後に出しました。第 5 章のモノイド、第 7 章のファンクタと同じ順序です。**困りごとから出発して、構造を作り、名前を知る。**

### 何が分かれたのか

| | コマンド側 | クエリ側 |
| :--- | :--- | :--- |
| 見るもの | `ToDoListState` | `ToDoListProjection` |
| 目的 | 業務ルールを判断する | 表示に答える |
| 書き込み | する（イベントを保存） | しない |
| 形を決める理由 | 判断に必要な情報 | 画面が欲しい形 |

**片方を変えても、もう片方に影響しません。** 新しい画面が必要になったら射影を足すだけで、コマンド側は変わりません。業務ルールが増えたら状態を変えるだけで、画面は変わりません。

### CQRS の代償

正直に書きます。

| 失うもの | 内容 |
| :--- | :--- |
| コード量 | 状態と射影で 2 つのモデルを持つ |
| 理解のコスト | 「どっちを見ればいいのか」を毎回考える |
| 一貫性 | 射影がイベントから作り直されるまで、古い値が見える可能性（本連載では毎回作り直すので起きない） |

**画面が 1 つしかないなら、CQRS は過剰です。** 効いてくるのは、画面が増えたとき、画面ごとに欲しい形が違うとき、参照の頻度が変更よりずっと高いときです。

Zettai は画面が 1 つですが、**この設計を説明するための題材**なので採用しています。

### インメモリのアダプタが不要になった

第 2 章から使ってきた `inMemoryFetcher` を削除しました。クエリ側が射影を見るようになったので、`ToDoList` を直接取り出すアダプタが要らなくなりました。

**役割を終えたコードは消します。** 第 2 章の `BuildSmokeTest` と同じ判断です。

## まとめ

この章でやったことを振り返ります。

- **困りごとから出発しました。** ドメインの状態と画面が欲しい形が違い、1 つの型で両方を満たそうとするとどちらにも最適でなくなります
- **表示用のモデルを射影として作りました。** 同じイベントの列を、別の形に畳み込みます。第 5 章の `replayFrom` とまったく同じ形で、違うのは畳み込む先だけです
- **射影の `map` がファンクタであることを確かめました。** 第 7 章と同じ 2 つの法則、同じテストの形、同じ生成器が使えました
- **コマンド側とクエリ側を分けました。** これが CQRS です。名前は最後に出しました
- **代償も書きました。** モデルが 2 つになり、理解のコストが増えます。画面が 1 つなら過剰です

イベントを残す設計の見返りが、この章で出ました。**状態を上書きしていたら、「別の形も欲しい」と言われたときに作り直せません。** イベントが残っていれば、後からいくつでも射影を作れます。

次の章で、そのイベントをデータベースに保存します。ここまでメモリの中にあったので、再起動すると消えていました。

---

## この章で書いたコード

- 射影: `apps/kotlin/zettai/zettai-step3-persistence/src/main/kotlin/zettai/domain/queries/ToDoListProjection.kt`
- ハブ（変更）: `.../domain/ToDoListHub.kt`
- テスト: `.../test/kotlin/zettai/domain/queries/ToDoListProjectionTest.kt`

この章から `zettai-step3-persistence` モジュールに移ります。モジュールは 2 章ごとに増やしています（[ADR-007](../../../adr/ADR-007-module-per-unit.md)）。

## 参照

- Uberto Barbini『From Objects to Functions』第 8 章。射影から CQRS に至る流れは本書に拠っています。本連載のコードはすべて書き起こした自作実装です
