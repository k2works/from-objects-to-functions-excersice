---
type: Article
title: "第 2 章 関数を使って HTTP を扱う"
description: "Zettai 連載 Scala 版の第 2 章。http4s の HttpRoutes と Ember で HTTP のウォーキングスケルトンを通し、IO を実行する境界と Resource による後始末を確かめる。"
tags: [article, zettai, scala, cats-effect, http4s, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-02T20:00:00Z }
---

# 第 2 章 関数を使って HTTP を扱う

この章では、`GET /todo/uberto/book` が HTML を返す最小の縦串を通します。HTTP サーバーは http4s の Ember で起動します。

Flix 版は JDK の `HttpServer` を Java 相互運用で呼び、効果の保証が切れる境界を記録しました。Scala 版では、HTTP サーバーも Scala のライブラリで、`IO` と `Resource` で扱えます。**境界は Java との間ではなく、`IO` を実行する場所に現れます。**

## プロジェクトのキックオフ

最初の到達点は `GET /todo/uberto/book` です。`uberto` の `book` リストがあれば項目を HTML で返し、なければ `404 Not Found` を返します。作成、更新、データベースはまだ扱いません。

この薄い縦串はウォーキングスケルトンです。HTTP、ドメイン、表示の接続を早く確かめ、後の章で安全に太くできる骨格を作ります。

まず外側から受入テストを書きました。サーバーを起動して本物の HTTP で叩きます。

```scala
  test("実際のサーバーを起動して、HTTP でリストを読む") {
    val resources =
      for
        server <- Server.start(ZettaiRoutes.routes(hub), port"0")
        client <- EmberClientBuilder.default[IO].build
      yield (server, client)

    resources.use { case (server, client) =>
      val base = s"http://127.0.0.1:${server.address.getPort}"
      for
        found   <- client.get(s"$base/todo/uberto/book")(r => r.as[String].map(b => (r.status, b)))
        missing <- client.get(s"$base/todo/uberto/missing")(r => IO.pure(r.status))
      yield
        assertEquals(found._1, Status.Ok)
        assert(found._2.contains("<li>write &lt;chapter&gt;</li>"))
        assertEquals(missing, Status.NotFound)
    }
  }
```

`Server` はまだ存在しないため、コンパイルが失敗します。これを Red とし、テストを通すのに必要なものだけを足します（パスは省略しています）。

```bash
[error] -- [E006] Not Found Error: src/test/scala/zettai/ServerSuite.scala:17:18
[error] 17 |        server <- Server.start(ZettaiRoutes.routes(hub), port"0")
```

ポート `0` は OS に空きポートを選ばせる指定です。固定ポートを使わないので、テストが他のプロセスと衝突しません。

`Resource` は、サーバーとクライアントの起動と停止を 1 つの値にまとめます。`use` の中でだけ使え、抜けるときに（失敗しても）必ず止まります。Flix 版では、サーバーを止める `JdkServer.stop` を呼び忘れない規律が必要でした。ここでは型が後始末を担います。

## 関数型で HTML ページを提供する

HTTP を関数として見ると、リクエストを受けてレスポンスを返す関数です。http4s の `HttpRoutes[IO]` は、リクエストに対して**応答を返すかもしれない**関数で、`IO` の中で動きます。

この関数をサーバーから切り離して書けば、サーバーを起動せずに検証できます。テストを先に書きます。

```scala
  private val app = ZettaiRoutes.routes(hub).orNotFound

  private def get(path: String): IO[Response[IO]] =
    app.run(Request[IO](Method.GET, Uri.unsafeFromString(path)))

  test("リストを HTML で返す") {
    for
      response <- get("/todo/uberto/book")
      body     <- response.as[String]
    yield
      assertEquals(response.status, Status.Ok)
      assert(body.contains("<li>write chapter</li>"))
  }
```

`app.run` はネットワークを使わず、`Request` を渡して `IO[Response]` を受け取ります。このテストでも `IO` は作っただけでは動かず、`CatsEffectSuite` が実行します。

HTML の生成は、副作用のない関数です。特殊文字をエスケープします。

```scala
  def escape(text: String): String =
    text
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
```

```scala
  def render(list: ToDoList): String =
    val lis = list.items.map(renderItem).mkString
    s"<html><body><h1>Zettai</h1><h2>${escape(list.name.name)}</h2><ul>$lis</ul></body></html>"
```

ルートは、パスのパターンで `Hub` に問い合わせ、見つかれば HTML を返します。

```scala
  def routes(hub: Hub): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "todo" / user / list =>
        hub.getList(User(user), ListName(list)).flatMap {
          case Some(found) => Ok(Html.render(found)).map(_.withContentType(html))
          case None        => NotFound()
        }
    }
```

`GET` 以外のメソッドや知らないパスは、どのパターンにも合わないので `404` になります。Flix 版は `405` を返しましたが、http4s は一致しないものを `404` にします。`405` が必要なら、第 7 章のエラーの扱いで足します。

## Zettai の開発を始める

題材のドメインは、不変のデータで表します。Scala 3 の `case class` と `enum` を使います。

```scala
final case class User(name: String)

final case class ListName(name: String)

enum ToDoStatus:
  case Todo, InProgress, Done, Blocked

final case class ToDoItem(description: String, status: ToDoStatus)
```

```scala
final case class ToDoList(name: ListName, items: List[ToDoItem])
```

`User` と `ListName` は、どちらも `String` を包んだだけの型です。**包むことで、利用者名とリスト名の取り違えをコンパイラが止めます。** Kotlin 版の value class、Flix 版の 1 引数の `enum` に当たります。

見つける関数は純粋です。

```scala
  def findToDoList(lists: Map[User, List[ToDoList]], user: User, name: ListName): Option[ToDoList] =
    lists.get(user).flatMap(_.find(_.name == name))
```

## 矢印で設計する

読み取りの依存を、関数を持つ値として渡します。`Hub` は「利用者とリスト名から、リストを探す `IO`」を持つだけの値です。

```scala
final case class Hub(getList: (User, ListName) => IO[Option[ToDoList]])
```

メモリ上の実装は、純粋な `findToDoList` を `IO.pure` で包みます。

```scala
  def inMemory(lists: Map[User, List[ToDoList]]): Hub =
    Hub((user, name) => IO.pure(Domain.findToDoList(lists, user, name)))
```

`IO.pure` は、すでに計算済みの値を `IO` に入れます。第 9 章では、同じ `Hub` を PostgreSQL に問い合わせる実装に差し替えます。`IO` を返す型にしておくのは、そのためです。

### IO を実行する境界

サーバーの起動は `Resource` に閉じ込めます。

```scala
  def start(routes: HttpRoutes[IO], port: Port): Resource[IO, Http4sServer] =
    EmberServerBuilder
      .default[IO]
      .withHost(host"127.0.0.1")
      .withPort(port)
      .withHttpApp(routes.orNotFound)
      .build
```

アプリケーションの入口は `IOApp` です。**`IO` を実行するのは、ここだけです。**

```scala
object Main extends IOApp:
```

```scala
  def run(args: List[String]): IO[ExitCode] =
    val hub = Hub.inMemory(Map(User("uberto") -> List(sample)))
    Server.start(ZettaiRoutes.routes(hub), port"8080").useForever.as(ExitCode.Success)
```

`run` が返すのは「サーバーを起動して動かし続ける」という `IO` の値で、実行は `IOApp` が行います。ここから外のコードは、`IO` の値を組み立てるだけにします。

ただし、Scala の型は `IO` の外の副作用を止めません。ルートの中で `println` を呼んでも、コンパイルは通ります。保証が切れる場所は、Flix のような Java との境界ではなく、**`IO` を使わずに書けてしまう場所**です。この章のコードは、副作用を `IO` に入れる規律で守っています。

## ToDo リストをマップで提供する

最後に、本物のリストを `Map` で持つ `Hub` を作り、縦串を通します。

```scala
  private val hub = Hub.inMemory(Map(User("uberto") -> List(book)))
```

`sbt test` は 13 本が通ります（ドメイン 3、HTML 2、ルート 4、サーバー 1 に、第 1 章の 3 本）。

テストでは、ログ実装の SLF4J が見つからない警告が出ました。Ember が内部でログを使うためです。テストの依存に `slf4j-nop` を足して抑えました。本番のログは第 12 章で扱います。

## まとめ

この章では、http4s の Ember で HTTP のウォーキングスケルトンを通し、次を確認しました。

- ルートは `HttpRoutes[IO]` で、サーバーを起動せずにテストできる
- サーバーとクライアントの起動と停止は `Resource` に閉じ込められ、`use` を抜けると必ず止まる
- 依存は、関数を持つ値（`Hub`）として渡せる。実装は第 9 章で差し替える
- `IO` を実行するのは `IOApp` とテストの境界だけ。ただし `IO` を使わない副作用は、型では止められない
- http4s は、一致しないメソッドやパスを `404` にする

http4s の版は、Maven Central の最新が 1.0.0-M49 というマイルストーンでした。連載は**安定版の 0.23.38 を使います**。1.0 が安定版になったら、移行の影響を別に記録します。

次章では、HTTP を通る受入テストと、ドメインだけを通る受入テストを、同じシナリオで実行できるようにします。

## この章で書いたコード

- `apps/scala/zettai/src/main/scala/zettai/Domain.scala`
- `apps/scala/zettai/src/main/scala/zettai/Html.scala`
- `apps/scala/zettai/src/main/scala/zettai/Hub.scala`
- `apps/scala/zettai/src/main/scala/zettai/ZettaiRoutes.scala`
- `apps/scala/zettai/src/main/scala/zettai/Server.scala`
- `apps/scala/zettai/src/main/scala/zettai/Main.scala`
- `apps/scala/zettai/src/test/scala/zettai/ServerSuite.scala`
- `apps/scala/zettai/src/test/scala/zettai/ZettaiRoutesSuite.scala`

## 参照

- [http4s 公式ドキュメント](https://http4s.org/)
- [Cats Effect の Resource](https://typelevel.org/cats-effect/docs/std/resource)
