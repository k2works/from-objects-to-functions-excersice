---
type: Article
title: "第 8 章 ファンクタを使ってイベントを射影する"
description: "Zettai 連載 Scala 版の第 8 章。イベントを画面向けの行に射影し、IO の中の List を Nested の二重の map で変換する。射影と状態が規則を共有することを全パターンで確かめ、読み取り専用の入口で CQRS を表す。"
tags: [article, zettai, scala, cats-effect, projection, cqrs, functor, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-02T12:00:00Z }
---

# 第 8 章 ファンクタを使ってイベントを射影する

ここまでで、書く側（コマンドからイベントを作る）は整いました。この章では、読む側を作ります。イベントを **画面が欲しい形** に畳み込み、その結果を `map` で変換します。

## イベントを射影する

### 画面が欲しいものは違う

業務の判断に使う状態は、リストの中の項目を一つずつ持っています。一方、利用者の一覧画面が欲しいのは、リストごとの **項目の数と完了の数** だけです。

状態をそのまま画面に出すと、画面の都合が業務の状態の形を縛ります。逆に、画面の都合で状態の形を変えると、コマンドの判断が絡みます。

### 表示用のモデルを別に作る

画面に出したい形を、別の型にします。

```scala
final case class ToDoListRow(name: ListName, total: Int, done: Int)
```

### 第 5 章と同じ形

状態を作るときは、イベントを `foldLeft` で畳み込みました。射影も、**畳み込む先が違うだけ**で、同じ形です。

### 射影を実装する

テストを先に書きます。

```scala
  test("リストごとに、項目の数と完了の数を数える") {
    assertEquals(
      Projection.projectFrom(uberto, events).sortBy(_.name.name),
      List(ToDoListRow(book, 2, 1), ToDoListRow(food, 0, 0))
    )
  }
```

`Projection` がまだ無い間の Red は、名前の解決エラーでした。実装は、途中経過を `Map` に持ち、最後に行へ写します。

```scala
  private def step(lists: Lists, event: ToDoListEvent): Lists =
    event match
      case ToDoListEvent.ListCreated(_, name) => lists.updated(name, Nil)
      case ToDoListEvent.ItemAdded(_, name, item) =>
        lists.get(name).fold(lists)(items => lists.updated(name, items :+ item))
      case ToDoListEvent.ItemStatusChanged(_, name, description, status) =>
        lists.get(name).fold(lists)(items => lists.updated(name, Domain.changeStatus(items, description, status)))
```

```scala
  def projectFrom(user: User, events: List[ToDoListEvent]): List[ToDoListRow] =
    events
      .filter(owner(_) == user)
      .foldLeft(Map.empty: Lists)(step)
      .toList
      .map(toRow)
```

他の利用者のイベントは、行に含めません。

```scala
  test("他の利用者のイベントは含めない") {
    assertEquals(Projection.projectFrom(alice, events), List(ToDoListRow(book, 0, 0)))
  }
```

### 規則を共有して食い違いを防ぐ

射影を書くとき、状態の `apply` と射影の両方に「項目の状態を変える」規則が要ります。この規則を、それぞれが別々に書くと、食い違う余地ができます。

第 6 章で、規則を `Domain.changeStatus` という小さな関数に抜き出していました。状態の適用も射影も、これを呼びます。説明が一致する**最初の 1 件だけ**を変える規則です。

```scala
  test("同じ説明の項目があっても、状態を変えるのは最初の 1 件だけ") {
    val twin = List(
      ToDoListEvent.ListCreated(uberto, book),
      ToDoListEvent.ItemAdded(uberto, book, ToDoItem.todo("a")),
      ToDoListEvent.ItemAdded(uberto, book, ToDoItem.todo("a")),
      ToDoListEvent.ItemStatusChanged(uberto, book, "a", ToDoStatus.Done)
    )
    assertEquals(Projection.projectFrom(uberto, twin), List(ToDoListRow(book, 2, 1)))
  }
```

規則を共有しても、畳み込みの骨格は、状態と射影の 2 か所に残ります。2 つが一致することは、全パターンのテストで確かめます。

## ファンクタに対するクエリの実行

射影した結果を、画面向けの別の値に変換することを考えます。取り出した行に `map` をかけます。

読み出しは `IO` の中の `List` です。`IO` の `map` と `List` の `map` で、`map` が二重になります。Cats の `Nested` は、この二重の層を 1 つのファンクタとして扱います。

```scala
  private type Query[A] = Nested[IO, List, A]

  private def query(value: List[ToDoListRow]): Query[ToDoListRow] = Nested(IO.pure(value))
```

### 同じ法則が成り立つ

第 7 章で確かめた恒等則と合成則を、この二重の `map` で確かめます。

```scala
  test("二重の map は恒等則を満たす") {
    rows.foldLeft(IO.unit) { (done, value) =>
      done *> Functor[Query].map(query(value))(identity).value.map(assertEquals(_, value))
    }
  }
```

```scala
  test("二重の map は合成則を満たす") {
    val F = Functor[Query]
    rows.foldLeft(IO.unit) { (done, value) =>
      for
        _      <- done
        twice  <- F.map(F.map(query(value))(total))(show).value
        once   <- F.map(query(value))(total andThen show).value
      yield assertEquals(twice, once)
    }
  }
```

`IO` の失敗は、二重の層をそのまま通り抜けます。

```scala
  test("IO の失敗は、二重の層をそのまま通り抜ける") {
    val failing = Nested[IO, List, ToDoListRow](IO.raiseError(new RuntimeException("down")))
    Functor[Query].map(failing)(total).value.attempt.map { outcome =>
      assertEquals(outcome.left.map(_.getMessage), Left("down"))
    }
  }
```

これらのテストは、書いた時点ですでに通りました。確かめているのは Cats の `Nested` の性質で、新しく作った実装ではありません。**使う側として、ライブラリの性質を確かめた**ことになります。

## ファンクタの観点から考察する

ここまでで、`map` が出てくる型が並びました。

| 型 | `map` の意味 | 章 |
| :--- | :--- | :--- |
| `List[A]` | 各要素に適用する | 標準ライブラリ |
| `Either[E, A]` | 成功なら適用し、失敗はそのまま流す | 7 |
| `Nested[IO, List, A]` | `IO` の中の各要素に適用する | 8 |

どれも恒等則と合成則を満たします。ファンクタを 2 つ重ねても、法則は保たれます。

Kotlin 版は、`ToDoListProjection` に自作の `map` を持たせ、その法則を確かめました。Scala 版では、ファンクタ自体を自作せず、Cats の `Nested` を使いました。**自作は、画面向けの行の型（`ToDoListRow`）と畳み込みの規則だけです。**

状態と射影の一致は、全パターンで確かめます。2 人の利用者、1 つのリスト、1 つの項目、2 つの状態から作った 8 種類の出来事を、長さ 4 までのすべての列で並べます。

```scala
  test("射影は、イベントを再生した状態から数え直した行と一致する（全パターン）") {
    for events <- Gen.projectionListsUpTo(4); user <- List(uberto, alice) do
      assertEquals(Projection.projectFrom(user, events), rowsFromState(user, events))
  }
```

`rowsFromState` は、イベントを再生した状態から、行を数え直したものです。

```scala
  private def rowsFromState(user: User, events: List[ToDoListEvent]): List[ToDoListRow] =
    val state = Replay.replayFrom(Events.empty, events)
    Events.listFor(state, user, book).toList.map { list =>
      ToDoListRow(list.name, list.items.size, list.items.count(_.status == ToDoStatus.Done))
    }
```

このテストが本当に食い違いを検出できるのか、射影の規則を壊して確かめました。`Domain.changeStatus` を使わず、**説明が一致するすべての項目の状態を変える**ように書き換えると、同じ説明の項目のテストと、全パターンのテストの 2 本が落ちました。元に戻すと、すべて通ります。

## コマンドクエリ責務分離(CQRS)

ここまでで、書く側と読む側が分かれました。

- 書く側: コマンドを受け取り、状態を見て、イベントを追記する（第 6 章の `CommandHub`）
- 読む側: イベントを、画面向けの行に畳み込む（この章の `QueryHub`）

読む側の入口は、読み取り専用です。コマンドを実行する操作を、型として持ちません。

```scala
final case class QueryHub(listRows: User => IO[List[ToDoListRow]])
```

<!-- code-check: ignore この章を書いた時点のコード（コミット 98db184）。第 9 章で EventStore.load が版つきの値を返すようにした -->
```scala
  def queryHub(store: EventStore): QueryHub =
    QueryHub(user => store.load.map(events => Projection.projectFrom(user, events)))
```

`QueryHub` を受け取る関数は、コマンドを実行できません。**読むだけの関数が、書けないことを型が保証します。**

書く側で起きた変更が、読む側の行に現れることを確かめます。

```scala
  test("書く側で起きた変更が、読む側の行に現れる") {
    for
      store    <- EventStore.inMemory
      commands  = CommandHub.commandHub(store)
      queries   = QueryHub.queryHub(store)
      _        <- commands.execute(CreateToDoList(uberto, book))
      _        <- commands.execute(AddToDoItem(uberto, book, ToDoItem.todo("write")))
      _        <- commands.execute(ChangeItemStatus(uberto, book, "write", ToDoStatus.Done))
      rows     <- queries.listRows(uberto)
    yield assertEquals(rows, List(ToDoListRow(book, 1, 1)))
  }
```

2 つの入口は、同じ `EventStore` を見ています。読む側の行は、毎回イベントから畳み込み直しています。イベントが増えたときの読み出しの重さは、この章では扱いません。

## まとめ

- 画面向けの行を別の型にして、業務の状態から分けた
- 射影は、状態を作るときと同じ畳み込みの形で、畳み込む先が違うだけだった
- 状態の適用と射影が規則を共有し、全パターンで一致を確かめた。規則を壊すと 2 本が落ちる
- `IO` の中の `List` は、Cats の `Nested` で二重の `map` にできる。恒等則・合成則・失敗の通り抜けを確かめた
- 読み取り専用の入口（`QueryHub`）は、コマンドを実行する操作を型として持たない

`sbt test` は 62 本が通ります（第 7 章の 53 本に、9 本を足しました）。

つまずきとして、`Nested[IO, List, *]` と書くと、Scala 3.3 では `*` が見つからないというコンパイルエラーになりました。型の別名（`type Query[A] = Nested[IO, List, A]`）に置き換えました。

次章では、イベントストアを PostgreSQL に置き換えます。

## この章で書いたコード

- `apps/scala/zettai/src/main/scala/zettai/Projection.scala`
- `apps/scala/zettai/src/main/scala/zettai/QueryHub.scala`
- `apps/scala/zettai/src/test/scala/zettai/Gen.scala`
- `apps/scala/zettai/src/test/scala/zettai/ProjectionSuite.scala`
- `apps/scala/zettai/src/test/scala/zettai/QueryFunctorSuite.scala`
- `apps/scala/zettai/src/test/scala/zettai/QueryHubSuite.scala`

## 参照

- [Cats の Nested](https://typelevel.org/cats/datatypes/nested.html)
- [Cats の Functor](https://typelevel.org/cats/typeclasses/functor.html)
