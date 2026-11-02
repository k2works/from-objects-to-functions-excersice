---
type: Article
title: "第 3 章 ドメインの定義とテスト"
description: "Zettai 連載 Scala 版の第 3 章。受け入れテストの経路を、関数値を持つケースクラスと Resource で差し替え、同じシナリオをドメインと HTTP の両方で実行する。Cucumber を入れない判断を ADR-049 に記録する。"
tags: [article, zettai, scala, cats-effect, acceptance-test, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-02T21:00:00Z }
---

# 第 3 章 ドメインの定義とテスト

前章で、HTTP を通る最小の縦串ができました。この章では、同じ業務シナリオを **ドメインだけを通る経路** と **HTTP を通る経路** の両方で実行できるようにします。

片方だけ落ちたら、そこに業務のロジックが漏れています。経路の差し替えは、関数値を持つケースクラスと `Resource` で表します。

## 受け入れテストを改善する

第 2 章の `ServerSuite` は、サーバーを起動して HTTP を叩く 1 本のテストでした。業務の言葉（「リストの項目が見える」）と、HTTP の詳細（URL、ステータス、HTML の読み取り）が混ざっています。

業務の言葉だけを書いたシナリオにします。シナリオは、利用者が何をするかだけを知り、どう実行されるかを知りません。

```scala
  def seeTheItemsOfAList(actions: ZettaiActions): IO[Unit] =
    actions.getToDoList(User("uberto"), ListName("book")).map { found =>
```

```scala
  def aMissingListIsNotFound(actions: ZettaiActions): IO[Unit] =
    actions.getToDoList(User("uberto"), ListName("missing")).map(found => check(actions, None, found))
```

シナリオの引数 `actions` が、実行の経路です。

## 高階関数を使う

`ZettaiActions` は、シナリオが使う操作を並べた値です。名前と、リストを探す関数だけを持ちます。

```scala
final case class ZettaiActions(
    name: String,
    getToDoList: (User, ListName) => IO[Option[List[ToDoItem]]]
)
```

`getToDoList` は関数を持つフィールドです。経路ごとに別の関数を入れて、シナリオには同じ型の値として渡します。これは、シナリオが `ZettaiActions` を受け取る高階関数であることを意味します。

## ドメインとインフラストラクチャの分離

2 つの経路を作ります。

**ドメインだけを通る経路**は、HTTP を知りません。純粋な `findToDoList` を `IO.pure` で包みます。

```scala
  def domainActions(lists: Map[User, List[ToDoList]]): Resource[IO, ZettaiActions] =
    Resource.pure(
      ZettaiActions(
        "domain",
        (user, name) => IO.pure(Domain.findToDoList(lists, user, name).map(_.items))
      )
    )
```

**HTTP を通る経路**は、実際のサーバーを起動し、HTML を読み戻します。

```scala
  def httpActions(lists: Map[User, List[ToDoList]]): Resource[IO, ZettaiActions] =
    for
      server <- Server.start(ZettaiRoutes.routes(Hub.inMemory(lists)), port"0")
      client <- EmberClientBuilder.default[IO].build
    yield
      val base = s"http://127.0.0.1:${server.address.getPort}"
      ZettaiActions(
        "http",
        (user, name) =>
          client.get(s"$base/todo/${user.name}/${name.name}") { response =>
            if response.status == Status.Ok then response.as[String].map(body => Some(HtmlReader.items(body)))
            else IO.pure(None)
          }
      )
```

どちらの経路も `Resource[IO, ZettaiActions]` を返します。ドメインの経路は後始末が要らないので `Resource.pure` です。HTTP の経路は、サーバーとクライアントを `Resource` で起動し、`use` を抜けると止まります。**経路ごとに後始末の有無が違っても、シナリオからは同じ型に見えます。**

Flix 版では、ドメインの経路の純粋な関数を `IO` を期待する場所に入れるために、`unchecked_cast` を使う補助関数が要りました。Scala では `IO.pure` で値を包むだけで足ります。**`IO` は値なので、純粋な値を `IO` に入れる操作が言語の外にありません。**

### 同じシナリオをすべての経路で実行する

経路を並べて、順にシナリオを実行します。

```scala
  def onEveryRoute(lists: Map[User, List[ToDoList]])(scenario: ZettaiActions => IO[Unit]): IO[Unit] =
    List(domainActions(lists), httpActions(lists)).foldLeft(IO.unit) { (done, route) =>
      done *> route.use(scenario)
    }
```

`*>` は、左の `IO` を実行してから右の `IO` を実行します。経路を足すときは `List` に足すだけで、シナリオは変わりません。

## ドメインからテストを駆動する

受け入れテストは、データと、シナリオと、経路をつなぐだけです。

```scala
  test("リストの項目が見える") {
    Routes.onEveryRoute(lists)(Scenarios.seeTheItemsOfAList)
  }

  test("存在しないリストは見つからない") {
    Routes.onEveryRoute(lists)(Scenarios.aMissingListIsNotFound)
  }

  test("リストを持たない利用者は何も見つけられない") {
    Routes.onEveryRoute(Map.empty)(Scenarios.aMissingListIsNotFound)
  }
```

先にこのテストを書いた時点の Red は、`Routes` が無いというコンパイルエラーでした。

```bash
[error] -- [E006] Not Found Error: src/test/scala/zettai/AcceptanceSuite.scala:21:4
[error] 21 |    Routes.onEveryRoute(lists)(Scenarios.seeTheItemsOfAList)
```

項目の説明には、`read <today> & "FP"` のように、HTML の特殊文字を入れてあります。HTTP の経路は、エスケープされた HTML を読み戻して元に戻すので、ドメインの経路と同じ結果になるはずです。

### 経路が失敗を絞り込む

`Html.render` が項目の順序を壊した場合を試しました。次の 1 行を、一時的に書き換えます（試したあと元に戻しました）。

<!-- code-check: ignore わざと壊したコード -->
```scala
    val lis = list.items.reverse.map(renderItem).mkString
```

すると、`HtmlSuite` の 1 項目のテストは通り、受け入れテストの HTTP の経路だけが落ちました。失敗メッセージには経路の名前が出ます（日本語の表示は省略しています）。

```bash
==> X zettai.AcceptanceSuite.リストの項目が見える
values are not the same
=> Obtained
Tuple2(
  _1 = "http",
  _2 = Some(
    value = List(
      ToDoItem(
        description = "read <today> & \"FP\"",
        status = Todo()
      ),
```

`_1 = "http"` で、落ちた経路が分かります。`domain` は通っているので、ドメインの `findToDoList` は正しく、HTML の生成か HTTP の層に問題がある、と絞り込めます。

経路の名前を失敗メッセージに出すために、結果と名前を組にして比べています。

```scala
    assertEquals((actions.name, actual), (actions.name, expected))
```

## DDT を関数値のケースクラスに変換する

Kotlin 版は Pesticide の `DdtActions` で経路を差し替えました。Pesticide は Kotlin のライブラリで、Scala にはありません。Kotlin FP 版は Cucumber を使いました。

Scala 版は、**関数値を持つケースクラスで経路を差し替え、Cucumber を入れません。** 判断は [ADR-049](../../../adr/ADR-049-scala-acceptance-by-case-class-of-functions.md) に記録しました。

確かめたことと、確かめていないことを分けておきます。

- `cucumber-scala_3` は Maven Central にあります（9.2.1。2026-11-02 に確認）
- `IO` を返すテストと組み合わせて動かすことは、**試していません**

Cucumber が使えないと確かめたわけではありません。必要なのは経路の差し替えで、Gherkin ではないので、追加の依存を入れない判断をしました。非エンジニアがシナリオを読み書きする必要が出たときは、入れ直します。

## まとめ

この章では、受け入れテストの経路を差し替えられるようにし、次を確認しました。

- シナリオは `ZettaiActions` だけを受け取り、経路を知らない
- 経路は `Resource[IO, ZettaiActions]` で表す。後始末の有無が違っても、同じ型に見える
- 純粋な値を `IO` に入れるのは `IO.pure` だけで済む。Flix のような補助関数は要らない
- 経路を足してもシナリオは変わらない
- 片方の経路だけが落ちると、失敗メッセージの経路名から原因を絞り込める
- Cucumber は存在を確かめたが、試さずに採らなかった（ADR-049）

`sbt test` は 16 本が通ります（第 2 章の 13 本に、受け入れテストの 3 本を足しました）。

次章では、`Hub` のように依存を渡す形を、関数引数、`Resource`、タグレス・ファイナルのどれで表すかを比べます。

## この章で書いたコード

- `apps/scala/zettai/src/test/scala/zettai/ZettaiActions.scala`
- `apps/scala/zettai/src/test/scala/zettai/Scenarios.scala`
- `apps/scala/zettai/src/test/scala/zettai/Routes.scala`
- `apps/scala/zettai/src/test/scala/zettai/HtmlReader.scala`
- `apps/scala/zettai/src/test/scala/zettai/AcceptanceSuite.scala`

## 参照

- [ADR-049 受け入れテストの経路を関数値のケースクラスで差し替える](../../../adr/ADR-049-scala-acceptance-by-case-class-of-functions.md)
- [Cats Effect の Resource](https://typelevel.org/cats-effect/docs/std/resource)
