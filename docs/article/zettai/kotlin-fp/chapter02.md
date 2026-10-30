---
type: Article
title: "第 2 章 関数を使って HTTP を扱う"
description: "Zettai Kotlin FP 版の第 2 章。http4k の HttpHandler と suspend 関数を接続し、HTTP から HTML までのウォーキングスケルトンを TDD で通す。"
tags: [article, zettai, kotlin, arrow, functional-programming, http4k]
status: draft
generated: { by: openai-codex/gpt-5, at: 2026-10-30T05:24:30Z }
---

# 第 2 章 関数を使って HTTP を扱う

第 1 章では、純粋関数と effect の境界を小さな例で確認しました。この章では、その考え方を HTTP に持ち込みます。最初から完成形を積み上げるのではなく、リクエストが入り、ドメインの値を取得し、HTML が返る最小の縦串を通します。

## プロジェクトのキックオフ

最初の到達点は `GET /todo/uberto/book` です。`uberto` の `book` リストがあれば項目を HTML で返し、なければ `404 Not Found` を返します。作成、更新、データベースはまだ扱いません。

この薄い縦串はウォーキングスケルトンです。HTTP、ドメイン、表示の接続を早く確かめ、後の章で安全に太くできる骨格を作ります。

まず外側から受入テストを書きました。

```kotlin
"GET /todo/{user}/{list} はリストを HTML で返す" {
    val response = app(Request(GET, "/todo/uberto/book"))

    response.status shouldBe Status.OK
    response.bodyString() shouldContain "<h2>book</h2>"
    response.bodyString() shouldContain "<li>write chapter</li>"
    response.bodyString() shouldContain "<li>publish book</li>"
}
```

最初は `zettai.domain` も `zettaiHttpApp` も存在しないため、コンパイルが失敗します。これを Red とし、テストを通すのに必要なものだけを追加します。

## 関数型で HTML ページを提供する

http4k の `HttpHandler` は、概念上 `(Request) -> Response` です。サーバーを起動しなくても、関数へ `Request` を渡すだけで振る舞いを検証できます。

一方、今後のデータ取得は I/O になります。その事実を型へ先に表し、取得関数を `suspend` にします。

```kotlin
typealias ToDoListFetcher = suspend (User, ListName) -> ToDoList?

fun inMemoryFetcher(lists: Map<User, List<ToDoList>>): ToDoListFetcher =
    { user, listName -> findToDoList(lists, user, listName) }
```

インメモリ実装は実際には待ちません。それでもポートを `suspend` にしておけば、後で PostgreSQL を接続しても呼び出し側の契約を作り直さずに済みます。ただし、`suspend` を付けるだけで blocking JDBC が non-blocking になるわけではありません。dispatcher と blocking boundary は永続化を導入する第 9 章で決めます。

http4k の同期ハンドラとの接続には `runBlocking` が必要です。置く場所は最外周の 1 箇所だけです。

```kotlin
val todoList = runBlocking { fetchList(user, listName) }
```

`runBlocking` をドメインへ持ち込むと、純粋な判断と実行方法が混ざります。境界に限定すれば、内側は coroutine の起動方法を知りません。

## Zettai の開発を始める

ドメインは HTTP を知らない値だけで表します。

```kotlin
@JvmInline
value class User(val name: String)

@JvmInline
value class ListName(val name: String)

@JvmInline
value class ToDoItem(val description: String)
```

`String` のまま渡すより、`User` と `ListName` の取り違えをコンパイラが見つけられます。既存 Kotlin 版の `data class` に対し、Kotlin FP 版は同値性だけが必要な単一値を `value class` にしました。新しい抽象ライブラリは必要ありません。

HTTP アプリケーションは、パスをドメイン値へ変換し、取得関数を呼び、結果をレスポンスへ変換します。

```kotlin
fun zettaiHttpApp(fetchList: ToDoListFetcher): HttpHandler = routes(
    "/todo/{user}/{list}" bind GET to { request ->
        val user = User(request.path("user").orEmpty())
        val listName = ListName(request.path("list").orEmpty())
        val todoList = runBlocking { fetchList(user, listName) }

        todoList
            ?.let {
                Response(Status.OK)
                    .header("Content-Type", "text/html; charset=utf-8")
                    .body(renderHtml(it))
            }
            ?: Response(Status.NOT_FOUND)
    },
)
```

見つかった値を `let` で HTML へ写し、`Content-Type` を付けます。値がなければ 404 にします。この段階では `null` を採用しています。エラーの種類が増える第 7 章で Arrow の typed error に置き換えます。

この章ではネットワークの待受 server を起動しません。http4k の in-process `HttpHandler` 契約までを縦串とし、次のコマンドで再現します。

```bash
cd apps/kotlin-fp/zettai
./gradlew :zettai-step2-http-domain:check
```

## 矢印で設計する

依存の向きは、クラス図より関数の型へ直接現れます。

```text
Request -> HttpHandler -> ToDoListFetcher -> ToDoList -> HTML -> Response
```

`zettaiHttpApp` は保存方法を知りません。知っているのは `zettai.application.ToDoListFetcher` の入力と出力だけです。純粋な検索は `zettai.domain.findToDoList` にあり、web adapter は内側へ依存します。インメモリから PostgreSQL へ変えても、HTTP の判断は変わりません。

境界はテストでも守ります。`zettai.domain` の import を走査し、`org.http4k` と `zettai.web` が入っていないことを検査します。パッケージ名だけを規約にするのではなく、違反したら `check` を失敗させます。

## ToDo リストをマップで提供する

永続化より前に HTTP の設計を確かめたいので、データ供給元は `Map<User, List<ToDoList>>` です。

```kotlin
fun inMemoryFetcher(lists: Map<User, List<ToDoList>>): ToDoListFetcher =
    { user, listName -> findToDoList(lists, user, listName) }
```

入力の `Map` を閉じ込めた関数を返すので、テストは固定データを簡単に渡せます。可変の共有状態も外部サービスもありません。404 のテストも同じハンドラを直接呼ぶだけです。HTML 変換ではリスト名と項目を escape し、ドメイン値を markup として解釈させません。

```kotlin
"存在しないリストは 404 を返す" {
    app(Request(GET, "/todo/uberto/missing")).status shouldBe Status.NOT_FOUND
}
```

## まとめ

- HTTP から HTML までのウォーキングスケルトンを通しました。
- `HttpHandler` を関数として直接テストしました。
- データ取得を `suspend (User, ListName) -> ToDoList?` というポートで表しました。
- `runBlocking` を同期 HTTP adapter の最外周に限定しました。
- ドメインが http4k と web adapter を知らないことを境界テストで守りました。

次章では、この HTTP 詳細を受入仕様から追い出します。同じ業務シナリオを domain と HTTP の 2 経路で実行し、adapter が振る舞いを変えていないことを確かめます。

## この章で書いたコード

- ドメイン: `apps/kotlin-fp/zettai/zettai-step2-http-domain/src/main/kotlin/zettai/domain/ToDoList.kt`
- 取得ポート: `apps/kotlin-fp/zettai/zettai-step2-http-domain/src/main/kotlin/zettai/application/ToDoListFetcher.kt`
- HTTP adapter: `apps/kotlin-fp/zettai/zettai-step2-http-domain/src/main/kotlin/zettai/web/`
- HTTP テスト: `apps/kotlin-fp/zettai/zettai-step2-http-domain/src/test/kotlin/zettai/web/ZettaiHttpAppTest.kt`
- 境界テスト: `apps/kotlin-fp/zettai/zettai-step2-http-domain/src/test/kotlin/zettai/smoke/DomainBoundaryTest.kt`

## 参照

- Uberto Barbini『From Objects to Functions』第 2 章
- [http4k](https://www.http4k.org/)
