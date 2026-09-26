---
type: Article
title: "第 2 章 関数を使って HTTP を扱う"
description: "Zettai 連載 Kotlin 版の第 2 章。http4k で HTTP のリクエストから ToDo リストの HTML までを最小の厚みで通し、ウォーキングスケルトンを作る。HttpHandler が (Request) -> Response の関数であることを軸に矢印で設計し、ドメインとアダプタを分け、リストの供給元をインメモリの Map に置く過程を TDD で示す。"
tags: [article, zettai, kotlin, chapter]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-26T13:54:52Z }
---

# 第 2 章 関数を使って HTTP を扱う

前章で環境を作り、テストを先に書く前提を置きました。この章から Zettai の実装に入ります。

やることは 1 つです。**HTTP のリクエストを受けて ToDo リストを表示する**。それだけを、できるだけ薄く通します。

## プロジェクトのキックオフ

新しいアプリケーションを作るとき、最初に何を書くかで進み方が変わります。

よくあるのは、データベースの設計から始める進め方です。エンティティを洗い出し、テーブルを決め、リポジトリを作り、それからサービス層を書く。積み上げていく感じがあって安心できます。

しかし、この順序には問題があります。**最初に動くものが出てくるのが遅い**のです。テーブルとリポジトリができた時点では、まだ何も動きません。画面に何かが出るのは、全部の層が揃ってからです。

そして層が揃うまで、設計が正しいかどうかを確かめられません。「このテーブル設計で画面が作れるか」は、画面を作ってみるまで分かりません。

### ウォーキングスケルトンから始める

そこで、逆から進めます。**一番外側から、一番内側まで、最小の厚みで一度通す。** これをウォーキングスケルトン（歩けるスケルトン）と呼びます。

この章で作るものは、骨と皮だけです。

- ToDo リストは 1 つだけ表示できる。作成も編集もできない
- データはメモリの中にある。データベースは使わない
- 項目には説明しかない。期限も状態もない
- エラーは 404 を返すだけ

これで何が分かるのか。**全体の形が分かります。** HTTP のリクエストがどこで受け取られ、どこでドメインの言葉に変わり、どこで HTML になるのか。この道筋さえ決まれば、残り 11 章はその道筋の内側を太くしていく作業になります。

### この章の到達点

`GET /todo/uberto/book` にアクセスすると、uberto さんの book というリストの項目が HTML で表示される。存在しないリストなら 404 が返る。これだけです。

## 関数型で HTML ページを提供する

Web フレームワークには [http4k](https://www.http4k.org/) を使います。選んだ理由は、この連載の主題に直結しています。

http4k では、**Web アプリケーションが関数です。**

```text
HttpHandler = (Request) -> Response
```

これだけです。リクエストを受け取ってレスポンスを返す関数。それが http4k における Web アプリケーションの定義です。

この定義が効いてきます。関数なら合成できます。関数なら、テストで直接呼べます。サーバーを起動しなくても、`Request` を作って渡せば `Response` が返ってきます。

### 依存を追加する

`gradle/libs.versions.toml` に http4k を足します。

```toml
[versions]
http4k = "6.15.1.0"

[libraries]
http4k-core = { module = "org.http4k:http4k-core", version.ref = "http4k" }
http4k-server-jetty = { module = "org.http4k:http4k-server-jetty", version.ref = "http4k" }
http4k-client-jetty = { module = "org.http4k:http4k-client-jetty", version.ref = "http4k" }
```

モジュール側の `build.gradle.kts` で使います。

```kotlin
dependencies {
    implementation(rootProject.libs.http4k.core)
    implementation(rootProject.libs.http4k.server.jetty)

    testImplementation(rootProject.libs.http4k.client.jetty)
    testImplementation(rootProject.libs.pesticide.core)
}
```

原著は http4k 4.48 を使っていますが、本連載は **6.15** を使います。第 2 章で扱う範囲では API に差分がないことを確かめました。判断の経緯は [ADR-003](../../../adr/ADR-003-http4k-6.md) にあります。

## Zettai の開発を始める

テストから書きます。前章で置いた前提のとおりです。

### 受け入れテストを書く（Red）

一番外側、つまり HTTP のレベルで書きます。

```kotlin
class SeeATodoListTest {

    private val listName = ListName("book")
    private val user = User("uberto")
    private val items = listOf("write chapter", "insert code", "publish book")

    private val zettai = Zettai(
        inMemoryFetcher(mapOf(user to listOf(ToDoList(listName, items.map(::ToDoItem)))))
    )

    @Test
    fun `ToDo リストの項目が HTML に含まれる`() {
        val response = zettai(Request(Method.GET, "/todo/uberto/book"))

        expectThat(response.status).isEqualTo(Status.OK)
        items.forEach { expectThat(response.bodyString()).contains(it) }
    }
}
```

注目してほしいのは `zettai(Request(...))` の部分です。**サーバーを起動していません。** `Zettai` が関数なので、直接呼んでいます。

テストの実行は速く、ポートの奪い合いも起きません。これが「Web アプリケーションが関数である」ことの実際の利益です。

実行すると、当然 Red です。

```text
e: SeeATodoListTest.kt:10:15 Unresolved reference 'domain'.
e: SeeATodoListTest.kt:26:26 Unresolved reference 'Zettai'.
```

### ドメインとアダプタの境界を決める

実装に入る前に、1 つ決めておきます。**どこからがドメインで、どこからが HTTP の話か。**

この線引きは、この先 11 章を縛ります。あとから引き直すと全部に響くので、ここで決めます。

```text
src/main/kotlin/zettai/
├── domain/     ドメイン。HTTP を知らない
│   └── ToDoList.kt
└── web/        アダプタ。HTTP を知っている
    ├── Zettai.kt
    ├── HtmlPage.kt
    └── InMemoryToDoListFetcher.kt
```

境界の定義はシンプルです。**`domain` パッケージのファイルは http4k を import しない。** import 文の不在が、そのまま境界になります。

これはコメントや命名規約より強い制約です。破ろうとすれば import を書くことになり、レビューで目に付きます。

### ドメインを書く（Green）

ToDo リストを表示するのに要る型だけを置きます。

```kotlin
package zettai.domain

data class User(val name: String)

data class ListName(val name: String)

data class ToDoItem(val description: String)

data class ToDoList(val listName: ListName, val items: List<ToDoItem>)
```

4 つだけです。

**ここで作り込まないことが重要です。** ToDo 項目には本来、期限（`dueDate`）と状態（`status`）があります。仕様としては第 1 章で決めています。しかし、この章では**表示するのに要らない**ので追加しません。

先回りして追加すると、記事で説明していないコードが残ります。そして「なぜこのフィールドがあるのか」を説明できないまま次の章に進むことになります。期限と状態は第 4 章で、必要になったときに追加します。

`String` ではなく `ListName` や `User` という型を作っているのは、意味の違う文字列を取り違えないためです。`fetchList(listName, user)` と引数を逆にしても、`String` なら通ってしまいます。

## 矢印で設計する

ここからがこの章の主題です。

`Zettai` を実装します。`HttpHandler` を実装する、というのは `(Request) -> Response` の関数になる、ということです。

```kotlin
class Zettai(private val fetchList: ToDoListFetcher) : HttpHandler {

    private val routes = routes(
        "/todo/{user}/{list}" bind Method.GET to ::showList
    )

    override fun invoke(request: Request): Response = routes(request)

    private fun showList(request: Request): Response {
        val user = User(request.path("user").orEmpty())
        val listName = ListName(request.path("list").orEmpty())

        val todoList = fetchList(user, listName) ?: return Response(Status.NOT_FOUND)

        return Response(Status.OK).body(renderHtml(todoList))
    }
}
```

`invoke` を実装すると、インスタンスを関数のように呼べます。`zettai(request)` と書けるのはこのためです。

### 矢印としての依存

コンストラクタ引数の型を見てください。

```kotlin
typealias ToDoListFetcher = (User, ListName) -> ToDoList?
```

`Zettai` が依存しているのは、**インターフェースではなく関数の型**です。「`User` と `ListName` を受け取って、`ToDoList` か null を返す何か」。それ以上のことを `Zettai` は知りません。

オブジェクト指向なら、ここにインターフェースを置くところです。

```text
interface ToDoListRepository {
    fun findByUserAndName(user: User, listName: ListName): ToDoList?
}
```

やっていることは同じに見えます。しかし違いがあります。

| | インターフェース | 関数の型 |
| :--- | :--- | :--- |
| 実装を渡す | クラスを定義して実装する | **ラムダを渡せる** |
| テストの差し替え | モックライブラリか、テスト用クラス | その場でラムダを書く |
| 合成 | デコレータパターンなどが要る | **関数合成できる** |
| 名前 | 実装クラスに名前が要る | 要らない |

「テストのために実装クラスを作る」という手間が消えます。テストで別の振る舞いが欲しければ、`{ _, _ -> null }` と書くだけです。

この「矢印」（`->`）で依存を表す設計は、第 4 章で **関数型の依存性注入**として本格的に扱います。この章では、矢印を 1 本引いたところまでです。

### HTML を組み立てる

表示はアダプタ側の仕事です。

```kotlin
package zettai.web

import zettai.domain.ToDoList

/** ToDo リストを HTML に変換する。表示の都合はドメインに持ち込まない。 */
fun renderHtml(todoList: ToDoList): String =
```

ドメインの `ToDoList` を受け取って `String` を返すだけの関数です。クラスにする理由がないのでしていません。

テンプレートエンジンは使わず、文字列テンプレートで組み立てます。第 11 章でテンプレートエンジンに置き換えます。今の段階では、HTML を返せること自体が本質で、その作り方は本質ではありません。

## ToDo リストをマップで提供する

最後に、データの供給元です。

```kotlin
/**
 * インメモリの Map から ToDo リストを取り出す。
 *
 * 第 9 章で永続化に置き換える。それまでは、アプリケーションを動かすのに
 * データベースを用意しなくてよいことのほうが価値が大きい。
 */
fun inMemoryFetcher(lists: Map<User, List<ToDoList>>): ToDoListFetcher =
    { user, listName -> lists[user]?.firstOrNull { it.listName == listName } }
```

この関数は、`Map` を受け取って `ToDoListFetcher`（つまり関数）を返します。**関数を返す関数**です。

データベースを使わないのは手抜きではありません。**この章で確かめたいのは「HTTP からドメインまでの道筋」であって、「データの保存方法」ではない**からです。関心事を 1 つに絞ると、確かめたいことが確かめやすくなります。

そして `Zettai` は `ToDoListFetcher` としか話していないので、第 9 章で PostgreSQL 版の実装に差し替えても、`Zettai` のコードは 1 行も変わりません。矢印で依存を表した見返りです。

### 404 を返す

存在しないリストへのリクエストも試します。

```kotlin
    @Test
    fun `存在しないリストは 404 を返す`() {
        val response = zettai(Request(Method.GET, "/todo/uberto/missing"))

        expectThat(response.status).isEqualTo(Status.NOT_FOUND)
    }
```

実装では `?:` で返しています。

<!-- code-check: ignore 上の Zettai.kt からの抜粋。エルビス演算子の行だけを取り出している -->

```kotlin
val todoList = fetchList(user, listName) ?: return Response(Status.NOT_FOUND)
```

「リストが無い」ことを `null` で表しているのが気になるかもしれません。気になって正解です。**null は「なぜ無いのか」を説明しません。** 権限が無いのか、名前が間違っているのか、削除されたのか、区別がつきません。

この問題は第 7 章「関数型手法によるエラーハンドリング」で `Outcome` という型を導入して解決します。今は素朴なままにしておきます。**問題を認識しつつ、解決を後の章に譲るのも設計判断です。**

### 縦串が通った

テストを実行します。

```text
SeeATodoListTest > ToDo リストの項目が HTML に含まれる() PASSED
SeeATodoListTest > 存在しないリストは 404 を返す() PASSED
```

ウォーキングスケルトンが通りました。

### 後片付け

第 1 章で置いたビルド基盤のスモークテスト（`BuildSmokeTest`）を削除します。**縦串の受け入れテストが、その役割を引き継いだ**からです。同じことを 2 箇所で確かめる必要はありません。

役割を終えたテストを残すと、何を守っているか分からないテストが積み上がります。消せるテストは消します。

## まとめ

この章でやったことを振り返ります。

- **ウォーキングスケルトンを通しました。** HTTP のリクエストから HTML まで、最小の厚みで一度通しました。全体の形が決まったので、残りの章はこの内側を太くする作業になります
- **ドメインとアダプタを分けました。** 境界は「`domain` パッケージが http4k を import しない」という形で表現しました。import の不在が制約になります
- **矢印で依存を表しました。** `Zettai` が依存しているのは `ToDoListFetcher = (User, ListName) -> ToDoList?` という関数の型で、インターフェースではありません。第 9 章で永続化に差し替えても `Zettai` は変わりません
- **作り込みませんでした。** 期限も状態も永続化もエラーの型も、必要になる章まで持ち越しました

最後の点が、この章でいちばん意識したことです。ウォーキングスケルトンは「動く最小」であって「動く完成品」ではありません。薄いまま通すから、全体の形が早く分かります。

次の章では、この章で書いた受け入れテストを見直します。今のテストは HTTP の詳細（パス、ステータスコード、レスポンスボディの文字列）を直接扱っていて、実装を変えると壊れます。テストを業務の言葉で書けるようにします。

---

## この章で書いたコード

- ドメイン: `apps/kotlin/zettai/zettai-step1-http/src/main/kotlin/zettai/domain/ToDoList.kt`
- アダプタ: `apps/kotlin/zettai/zettai-step1-http/src/main/kotlin/zettai/web/`（`Zettai.kt`・`HtmlPage.kt`・`InMemoryToDoListFetcher.kt`）
- テスト: `apps/kotlin/zettai/zettai-step1-http/src/test/kotlin/zettai/web/SeeATodoListTest.kt`

本文のコードは上のファイルからの転記です。逐語でない抜粋には、その旨のマーカーを記事の中に置いています。**この対応は CI で機械的に検査しています。**

## 参照

- Uberto Barbini『From Objects to Functions』第 2 章。ウォーキングスケルトンと矢印による設計という考え方は本書に拠っています。本連載のコードはすべて書き起こした自作実装です
- [http4k のドキュメント](https://www.http4k.org/)
