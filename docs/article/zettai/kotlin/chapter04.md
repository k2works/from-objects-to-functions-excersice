---
type: Article
title: "第 4 章 ドメインとアダプタのモデリング"
description: "Zettai 連載 Kotlin 版の第 4 章。ToDo リストを変更するストーリーを起点に、ドメインの入口をハブとして切り出し、アダプタを関数の型で受け取る関数型の依存性注入を導入する。インターフェースによる DI との違い、関数型コードのデバッグの勘所、ドメインの型を業務の語彙で表すモデリングを TDD で示す。"
tags: [article, zettai, kotlin, chapter]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-26T15:25:30Z }
---

# 第 4 章 ドメインとアダプタのモデリング

前章で、受け入れテストを業務の言葉で書けるようにしました。ここからはドメインの中身を作っていきます。

この章から進め方が変わります。前章までは外側（受け入れテスト）から内側へ向かいました。ここからは**内側（ドメイン）から作り、外へ広げます**。扱う内容がドメイン層の中で完結するからです。イベントの畳み込みも、この章の依存性注入も、HTTP から見れば何も変わりません。

## ToDo リストを変更するための新しいストーリーを始める

新しいストーリーを置きます。

> 利用者として、ToDo リストに項目を追加したい。なぜなら、思いついたことを後で見返したいからだ。

まだ実装しません。このストーリーを実現しようとすると何が必要になるかを、先に考えます。

### 表示と変更の違い

これまで作ってきたのは「表示」でした。リストを取り出して HTML にするだけです。

「変更」は違います。

| | 表示 | 変更 |
| :--- | :--- | :--- |
| データの向き | 外へ出す | 中へ入れる |
| 必要なアダプタ | 取り出す 1 つ | 取り出す + 保存する |
| 失敗の種類 | 見つからない | 見つからない、権限がない、不正な入力 |

第 2 章では、リストを取り出す関数を `Zettai` が直接持っていました。

<!-- code-check: ignore 第 2 章時点のコード。この章でハブに置き換える -->

```kotlin
class Zettai(private val fetchList: ToDoListFetcher) : HttpHandler {
```

アダプタが 1 つのうちはこれで足ります。2 つ、3 つと増えると、`Zettai` のコンストラクタが長くなります。そして HTTP の層が、ドメインの操作をどう組み立てるかを知ることになります。

### ハブを置く

そこで、ドメインの入口を 1 つ作ります。**ハブ**と呼びます。

```plantuml
@startuml
title ハブを置く前と後

package "前" {
  object Zettai1 as "Zettai"
  object Fetcher1 as "fetchList"
  Zettai1 --> Fetcher1
}

package "後" {
  object Zettai2 as "Zettai"
  object Hub as "ToDoListHub"
  object Fetcher2 as "fetchList"
  Zettai2 --> Hub
  Hub --> Fetcher2
}

note bottom of Hub
  アダプタが増えても
  Zettai は Hub しか知らない
end note
@enduml
```

HTTP の層が知るのはハブだけです。アダプタが増えてもハブの内側で吸収されます。

## 関数型の「依存性の注入」を使う

ハブを書きます。テストからです。

<!-- code-check: ignore 第 4 章時点のコード。第 6 章で引数が増え、第 7 章で戻り値が Outcome に変わった -->

```kotlin
    @Test
    fun `リストを取り出す`() {
        val hub = ToDoListHub { u, l -> if (u == user && l == listName) ToDoList(listName, items) else null }

        expectThat(hub.getList(user, listName)?.items).isEqualTo(items)
    }
```

**ここに注目してください。アダプタの実装クラスを用意していません。** `{ u, l -> ... }` というラムダを渡しているだけです。

### インターフェースなら何が要るか

同じことをインターフェースで書くと、こうなります。

<!-- code-check: ignore 比較のための擬似コード。本連載では採用しないため実装していない -->

```kotlin
interface ToDoListRepository {
    fun findBy(user: User, listName: ListName): ToDoList?
}

class FakeToDoListRepository(private val data: Map<Pair<User, ListName>, ToDoList>) : ToDoListRepository {
    override fun findBy(user: User, listName: ListName): ToDoList? = data[user to listName]
}
```

テストを書く前に、**テストのためのクラスを 1 つ作る**ことになります。振る舞いを変えたければ、また別のクラスを作るか、コンストラクタ引数を増やします。

モックライブラリを使えばクラスは書かずに済みますが、代わりに「モックの設定の書き方」を覚えることになります。

### 関数の型なら

ハブの定義はこうです。

<!-- code-check: ignore 第 4 章時点のコード。第 6 章で引数が増え、第 7 章で戻り値が Outcome に変わった -->

```kotlin
/** ToDo リストを取り出す。見つからなければ null。 */
typealias ToDoListFetcher = (User, ListName) -> ToDoList?

/**
 * ドメインの入口。
 *
 * アダプタを関数の型で受け取る。インターフェースを定義しないので、
 * 呼ぶ側はラムダを渡すだけでよく、テスト用の実装クラスが要らない。
 */
class ToDoListHub(private val fetchList: ToDoListFetcher) {

    fun getList(user: User, listName: ListName): ToDoList? = fetchList(user, listName)
}
```

これだけです。`ToDoListFetcher` は第 2 章で引いた矢印をそのまま持ってきました。置き場所がアダプタ側（`web`）からドメイン側（`domain`）に移っています。**ポートはドメインが決めるもの**だからです。

テストで別の振る舞いが欲しければ、その場でラムダを書きます。

<!-- code-check: ignore 第 4 章時点のコード。第 6 章で引数が増え、第 7 章で戻り値が Outcome に変わった -->

```kotlin
    @Test
    fun `見つからなければ null`() {
        val hub = ToDoListHub { _, _ -> null }

        expectThat(hub.getList(user, ListName("missing"))).isNull()
    }
```

呼ばれた回数を数えたければ、変数を閉じ込めます。

<!-- code-check: ignore 第 4 章時点のコード。第 6 章で引数が増え、第 7 章で戻り値が Outcome に変わった -->

```kotlin
    @Test
    fun `アダプタが呼ばれた回数を数えられる`() {
        var calls = 0
        val hub = ToDoListHub { _, _ ->
            calls++
            null
        }

        hub.getList(user, listName)
        hub.getList(user, listName)

        expectThat(calls).isEqualTo(2)
    }
```

モックライブラリの `verify(...)` に相当することが、`var calls = 0` で書けています。**新しい語彙を覚える必要がありません。** Kotlin を知っていれば読めます。

> **第 6〜7 章で変わります。** この章のハブは引数が 1 つで、戻り値が `ToDoList?` です。第 6 章で引数が 3 つになり、第 7 章で戻り値が `Outcome` になります。この章のコードは「第 4 章時点のもの」として読んでください。

### 何が違うのか

整理します。

| | インターフェース | 関数の型 |
| :--- | :--- | :--- |
| テスト用の実装 | クラスかモックが要る | ラムダで書ける |
| 覚えること | モックライブラリの API | なし |
| 名前 | 実装クラスに名前が要る | 要らない |
| メソッドが複数あるとき | 1 つのインターフェースにまとめられる | **関数を複数渡すことになる** |
| 合成 | デコレータパターンなど | **関数合成できる** |

最後の 2 行が、使い分けの目安です。**関連する操作が 3 つも 4 つもあるなら、インターフェースのほうが収まりがいい**こともあります。本連載でハブが受け取るアダプタは、この章では 1 つ、第 6 章で 2 つです。その規模なら関数の型が軽い。

「関数型だから常に関数の型」ではありません。**渡すものが 1 つか 2 つの関数なら、インターフェースを作る理由がない**というだけです。

### HTTP の層をハブ経由にする

`Zettai` を書き換えます。

```kotlin
class Zettai(private val hub: ToDoListHub) : HttpHandler {
```

中身も 1 行だけ変わります。

<!-- code-check: ignore Zettai.kt の showList からの抜粋 -->

```kotlin
val todoList = hub.getList(user, listName) ?: return Response(Status.NOT_FOUND)
```

受け入れテストは変えていません。外から見た振る舞いが変わっていないので、当然です。**変えずに green のままであることが、リファクタリングが成功した証拠です。**

## 関数型コードをデバックする

関数型で書くと、デバッグの勘所が変わります。

### スタックトレースが変わる

ラムダを渡す設計では、スタックトレースに `invoke` や `Function2` のような名前が並びます。「どのクラスの何というメソッドか」がすぐには読めません。

インターフェースなら `FakeToDoListRepository.findBy` と出るので、そこは分かりやすい。**これは関数の型のコストです。** 隠さずに書いておきます。

### 代わりに得られるもの

一方、関数型のコードには**追いやすさ**があります。

| | 手続き的なコード | 関数型のコード |
| :--- | :--- | :--- |
| 状態 | オブジェクトの中で変わる | 引数と戻り値だけ |
| 「今の値は何か」 | デバッガで覗く | **戻り値を見れば分かる** |
| 再現 | 同じ手順を踏む必要がある | **同じ引数を渡せば同じ結果** |

デバッガでステップ実行する代わりに、**テストを 1 本書けば同じことが確かめられます**。しかもそのテストは残ります。デバッガで確かめたことは残りません。

本連載では、デバッガを使う代わりに「確かめたいことをテストにする」進め方を採ります。

## 関数型ドメインモデリング

最後に、ドメインの型を太くします。第 1 章で決めた仕様のうち、まだ書いていないものがありました。

> 項目は説明と、任意の期限を持つ
> 項目には状態がある（未着手・進行中・完了・ブロック中）

型に反映します。

```kotlin
enum class ToDoStatus { Todo, InProgress, Done, Blocked }

data class ToDoItem(
    val description: String,
    val dueDate: LocalDate? = null,
    val status: ToDoStatus = ToDoStatus.Todo
)
```

### 型で表せることは型で表す

`status` を `String` にしていたら、`"done"` と `"Done"` と `"DONE"` の区別を実行時に考えることになります。`enum` にすれば、ありえない値がコンパイル時に弾かれます。

`dueDate` は `LocalDate?` です。null 許容にしているのは、**期限が無いことが正常だから**です。第 2 章で問題にした `null`（「なぜ無いのか分からない」）とは意味が違います。

| null の使い方 | 意味 | 評価 |
| :--- | :--- | :--- |
| `dueDate: LocalDate?` | 期限が無い。それが正常 | 適切 |
| `fetchList(...): ToDoList?` | 見つからない。理由は分からない | **不適切。第 7 章で直す** |

後者は第 7 章で `Outcome` という型に置き換えます。**「見つからない」「権限がない」「壊れている」を区別できないままでは、利用者に何を伝えればいいか決められない**からです。

この章でも直しません。第 7 章で扱う概念（ファンクタ）と一緒に説明したほうが、なぜその形になるのかが分かるからです。**問題を認識したまま先に進むのは、忘れて進むのとは違います。**

### 既定値の置き方

`dueDate` と `status` に既定値を与えました。既存のコードが `ToDoItem("write chapter")` のまま動きます。

これは手抜きに見えるかもしれませんが、意図があります。**この章の主題は依存性注入であって、状態遷移ではありません。** `status` を使った振る舞い（着手する、完了する）は第 6 章のコマンドで扱います。ここでは型に場所を作るだけにとどめます。

型に場所を作っておくと、第 6 章で振る舞いを足すときに、型の変更と振る舞いの追加を同時にやらずに済みます。

## まとめ

この章でやったことを振り返ります。

- **ハブを置きました。** HTTP の層が知るのはハブだけになり、アダプタが増えてもその内側で吸収されます
- **アダプタを関数の型で受け取りました。** テスト用の実装クラスもモックライブラリも要らず、ラムダで振る舞いを書けます。ただし**万能ではありません**。操作が 3 つも 4 つもあるならインターフェースのほうが収まります
- **デバッグの勘所が変わることを確かめました。** スタックトレースは読みにくくなりますが、確かめたいことをテストにできるので、デバッガに頼る場面が減ります
- **ドメインの型を太くしました。** 状態は `enum` で、期限は null 許容で表しました。`fetchList` の `null` は別の問題で、第 7 章まで持ち越します

次の章では、状態の変更を扱います。ただし「上書きする」のではなく、「起きたことを並べて残す」形にします。そこから、この連載で最初の抽象概念が現れます。

---

## この章で書いたコード

- ハブ: `apps/kotlin/zettai/zettai-step2-domain/src/main/kotlin/zettai/domain/ToDoListHub.kt`
- ドメインの型: `apps/kotlin/zettai/zettai-step2-domain/src/main/kotlin/zettai/domain/ToDoList.kt`
- テスト: `apps/kotlin/zettai/zettai-step2-domain/src/test/kotlin/zettai/domain/ToDoListHubTest.kt`

この章から `zettai-step2-domain` モジュールに移ります。`zettai-step1-http` は第 3 章時点のコードとして残してあります。

## 参照

- Uberto Barbini『From Objects to Functions』第 4 章。関数型の依存性注入という考え方は本書に拠っています。本連載のコードはすべて書き起こした自作実装です
