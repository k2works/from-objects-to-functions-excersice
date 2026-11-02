---
type: Article
title: "第 5 章 イベントで状態を変更する"
description: "Zettai 連載 Scala 版の第 5 章。変更をイベントとして残し、foldLeft で状態を導出する。状態変換を Cats の Monoid のインスタンスにし、法則を全パターンで確かめ、Cats の Endo と突き合わせる。"
tags: [article, zettai, scala, cats-effect, event-sourcing, monoid, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-02T23:00:00Z }
---

# 第 5 章 イベントで状態を変更する

この章では、変更を出来事（イベント）として残し、現在の状態を畳み込みで導出します。連載が自作してきたモノイドを、今度は Cats の型クラスとして宣言し、Cats が持つ別の実装と突き合わせます。

## ToDo リストの作成の表示

最初の出来事は、リストが作られたことです。命令の `CreateList` ではなく、すでに起きた事実なので、過去形の `ListCreated` と名付けます。

```scala
enum ToDoListEvent:
  case ListCreated(user: User, name: ListName)
  case ItemAdded(user: User, name: ListName, item: ToDoItem)
```

項目の追加も出来事です。`enum` は生成後に内容を変えられず、`match` はすべてのケースを列挙しなければコンパイルが通りません。この時点では `ListCreated` と `ItemAdded` の 2 種類だけで、状態変更や削除は先回りして足しません。

## 状態変更の保存

通常の CRUD は、保存先の値を上書きします。簡単ですが、前の状態と「なぜ変わったか」を捨てます。ここでは、出来事を保存する対象とし、現在の状態は必要なときに導出します。

導出先の状態も値です。

```scala
final case class ToDoListState(lists: Map[(User, ListName), ToDoList])
```

キーは `(User, ListName)` の組です。Scala の組はそのまま `Map` のキーにできます。

1 つの出来事を適用する関数は、新しい状態を返します。

```scala
  def apply(state: ToDoListState, event: ToDoListEvent): ToDoListState =
    event match
      case ToDoListEvent.ListCreated(user, name) =>
        put(state, user, name, ToDoList(name, Nil))
      case ToDoListEvent.ItemAdded(user, name, item) =>
        listFor(state, user, name) match
          case Some(ToDoList(_, items)) => put(state, user, name, ToDoList(name, items :+ item))
          case None                     => state
```

`updated` も `:+` も元の値を書き換えません。テストでは、空の状態、作成後、追加後の 3 つを同時に持ち、前の 2 つが変わっていないことを確かめます。

```scala
  test("イベントを適用しても、前の状態は変わらない") {
    val empty   = Events.empty
    val created = Events.apply(empty, ToDoListEvent.ListCreated(uberto, book))
    val added   = Events.apply(created, ToDoListEvent.ItemAdded(uberto, book, ToDoItem.todo("write chapter")))
    assertEquals(Events.listFor(empty, uberto, book), None)
    assertEquals(Events.listFor(created, uberto, book), Some(ToDoList(book, Nil)))
```

作成されていないリストへの `ItemAdded` は、状態を変えずに返します。

```scala
  test("作られていないリストへの追加は無視される") {
    val state = Events.apply(Events.empty, ToDoListEvent.ItemAdded(uberto, book, ToDoItem.todo("write chapter")))
    assertEquals(Events.listFor(state, uberto, book), None)
  }
```

これは、不正な履歴を拒否する仕組みではありません。出来事の列が正しいかどうかを検証するのは、コマンドを導入する第 6 章の仕事です。この章の `apply` は、渡された出来事を決定的に適用することだけに集中します。

## 再帰の力を解き放つ

1 件を適用できれば、列全体も適用できます。まず標準関数を使わず、再帰の形を見せます。

```scala
  @tailrec
  def replayByRecursion(initial: ToDoListState, events: List[ToDoListEvent]): ToDoListState =
    events match
      case Nil     => initial
      case e :: es => replayByRecursion(Events.apply(initial, e), es)
```

空なら初期状態を返します。空でなければ、先頭の出来事を適用した状態と、残りの出来事で同じ関数を呼びます。`@tailrec` は、この再帰が末尾再帰であることをコンパイラに検査させます。末尾再帰でなければ、コンパイルが通りません。

## イベントを畳み込む

再帰の定型を、標準の `foldLeft` に置き換えます。

```scala
  def replayFrom(initial: ToDoListState, events: List[ToDoListEvent]): ToDoListState =
    events.foldLeft(initial)(Events.apply)
```

`foldLeft` は初期値から始め、左から順に出来事を現在の状態へ取り込みます。可変の蓄積変数は見えません。

再帰版と `foldLeft` 版が同じ状態を返すことを、テストで確かめます。

```scala
  test("再帰版と畳み込み版は同じ状態を返す") {
    for seed <- seeds; events <- Gen.eventListsUpTo(3) do
      assertEquals(Replay.replayByRecursion(seed, events), Replay.replayFrom(seed, events))
  }
```

ここで使っている `Gen.eventListsUpTo(3)` は、乱数ではありません。

### 乱数ではなく、全パターンを並べる

プロパティテストのライブラリ（ScalaCheck 等）は、この版ではまだ入れていません。標準の道具だけで書けるかを先に確かめるためです。代わりに、**小さな範囲のすべてのパターンを並べる**ことにしました。

```scala
  def universe: List[ToDoListEvent] =
    val users = List(User("uberto"), User("alice"))
    val names = List(ListName("book"), ListName("food"))
    val items = List(ToDoItem.todo("a"), ToDoItem.todo("b"))
    val created = for u <- users; n <- names yield ToDoListEvent.ListCreated(u, n)
    val added   = for u <- users; n <- names; i <- items yield ToDoListEvent.ItemAdded(u, n, i)
    created ::: added
```

2 人の利用者、2 つのリスト名、2 つの項目から、起こりうる出来事は 12 種類です。これを長さ 0 から n までの列に並べます。

```scala
  def ofLength[A](k: Int, xs: List[A]): List[List[A]] =
    if k <= 0 then List(Nil)
    else for x <- xs; rest <- ofLength(k - 1, xs) yield x :: rest
```

長さ 3 までで 1,885 通りです（1 + 12 + 144 + 1,728）。乱数と違い、**この範囲のどこにも反例がないことが言えます**。一方で、範囲の外は何も言えません。乱数は広い範囲を薄く、全パターンは狭い範囲を厚く確かめます。この章の法則は、少数の要素の組み合わせで破れるものが多いので、全パターンを選びました。

## モノイドの発見

出来事を別の角度から見ます。出来事を 1 つ固定すると、「状態を受け取り、状態を返す関数」になります。

```scala
final case class StateTransition(run: ToDoListState => ToDoListState)
```

```scala
  def fromEvent(event: ToDoListEvent): StateTransition =
    StateTransition(state => Events.apply(state, event))
```

2 つの状態変換は、順番につなげられます。何もしない変換もあります。

```scala
  def andThen(a: StateTransition, b: StateTransition): StateTransition =
    StateTransition(state => b.run(a.run(state)))
```

つなぎ方と何もしない変換がそろい、2 つの法則が成り立つ構造を**モノイド**と呼びます。

- 結合律: `(a andThen b) andThen c` と `a andThen (b andThen c)` の結果は同じです。
- 単位元: 何もしない変換を左右どちらにつないでも結果は変わりません。

Cats には、この構造を表す型クラス `Monoid` が最初からあります。`StateTransition` にインスタンスを与えるだけで、`|+|` や `combineAll` が使えます。

```scala
  given Monoid[StateTransition] with
    def empty: StateTransition = StateTransition(state => state)
    def combine(a: StateTransition, b: StateTransition): StateTransition = andThen(a, b)
```

Kotlin FP 版では、`andThen` と `identity` を自分で書き、法則はテストで確かめるだけでした。Scala では、**構造を型クラスとして宣言できる**ので、標準の `combineAll` が使えます。

```scala
  def fromEvents(events: List[ToDoListEvent]): StateTransition =
    Monoid[StateTransition].combineAll(events.map(fromEvent))
```

出来事の列を、1 つの状態変換にまとめます。

### 法則を全パターンで確かめる

型クラスのインスタンスは、法則を守ることを**コンパイラは確かめません**。確かめるのはテストです。

```scala
  test("結合律") {
    for a <- transitions; b <- transitions; c <- transitions do
      assert(same((a |+| b) |+| c, a |+| (b |+| c)))
  }
```

```scala
  test("単位元") {
    for t <- transitions do
      assert(same(Monoid[StateTransition].empty |+| t, t))
      assert(same(t |+| Monoid[StateTransition].empty, t))
  }
```

関数は `==` で比べられないので、**用意した初期状態のすべてで同じ結果を返す**ことを、等しいことの定義にしました。

```scala
  private def same(a: StateTransition, b: StateTransition): Boolean =
    seeds.forall(seed => a.run(seed) == b.run(seed))
```

畳み込んだ変換が、出来事を順に適用した結果と等しいことも確かめます。

```scala
  test("畳み込んだ変換は、出来事を順に適用した結果と等しい") {
    for events <- Gen.eventListsUpTo(3); seed <- seeds do
      assertEquals(StateTransition.fromEvents(events).run(seed), Replay.replayFrom(seed, events))
  }
```

法則のテストが、本当に壊れた実装を検出できるのかも確かめました。`empty` を「常に空の状態を返す」関数に書き換えると、単位元のテストと畳み込みの等価のテストの 2 本が落ちました。元に戻すと、すべて通ります。

### Cats の Endo と突き合わせる

「状態を受け取り、状態を返す関数」は、Cats では `Endo[A]`（`A => A`）という名前で、`MonoidK` のインスタンスがすでにあります。自作の `StateTransition` は、Cats の既存の実装で置き換えられるでしょうか。

試すと、そのままでは結果が逆になりました。Cats の `Endo` の合成は `compose` の向きで、**右の関数を先に適用します**。自作の `andThen` は左を先に適用します。リストを `reverse` すると、全パターンで自作の畳み込みと一致しました。

```scala
  test("Cats の Endo のモノイドは、合成の向きが逆。reverse すると自作の畳み込みと一致する") {
    val endo = MonoidK[Endo].algebra[ToDoListState]
    for events <- Gen.eventListsUpTo(3); seed <- seeds do
      val viaCats = endo.combineAll(events.map(e => (s: ToDoListState) => Events.apply(s, e)).reverse)
      assertEquals(viaCats(seed), StateTransition.fromEvents(events).run(seed))
  }
```

置き換えられるかを整理すると、次のとおりです。

| 観点 | 自作の `StateTransition` | Cats の `Endo` |
| :--- | :--- | :--- |
| 結合律・単位元 | 全パターンで確かめた | Cats が保証する（本章では畳み込みの一致だけ確かめた） |
| 合成の向き | `andThen`（左が先） | `compose`（右が先） |
| 名前 | ドメインの言葉（`StateTransition`） | 一般的な名前（`Endo`） |
| 型 | `case class` で包む | `A => A` の型別名 |

**置き換えられますが、置き換えません。** 向きの違いは、読み手が間違えやすい箇所です。ドメインの名前を持つ型で包んでおくと、`reverse` の理由を 1 か所に閉じ込められます。

### 交換律は成り立たない

モノイドには、**交換律を求められません**。順番が結果を変える場合があり、それが正しい動作です。

```scala
  test("交換律は成り立たない。順番が結果を変える") {
    val created = StateTransition.fromEvent(ToDoListEvent.ListCreated(uberto, book))
    val added   = StateTransition.fromEvent(ToDoListEvent.ItemAdded(uberto, book, ToDoItem.todo("a")))
    assert(!same(created |+| added, added |+| created))
  }
```

「作成してから追加」と「追加してから作成」は結果が違います。後者では、リストがないまま追加が無視されるからです。

結合律があると、長い出来事の列を分割して別々に畳み込み、後で合成しても結果が変わりません。

```scala
  test("列を 2 つに分けて畳み込んでも、一度に畳み込んだ状態と同じ") {
    for a <- Gen.eventListsUpTo(2); b <- Gen.eventListsUpTo(2) do
      assertEquals(
        Replay.replayFrom(Replay.replayFrom(Events.empty, a), b),
        Replay.replayFrom(Events.empty, a ::: b)
      )
  }
```

これは、後の章で出来事の列が長くなったとき、並べ替えずに分割して処理できる根拠になります。

## まとめ

- 変更を出来事として残し、現在の状態を畳み込みで導出しました。
- 再帰版と `foldLeft` 版が同じ状態を返すことを、小さな範囲の全パターンで確かめました。
- 状態変換を Cats の `Monoid` のインスタンスにし、`combineAll` で出来事の列をまとめました。
- 結合律、単位元、畳み込みの等価を確かめ、`empty` を壊すとテストが落ちることも確かめました。
- Cats の `Endo` は、合成の向きが逆でした。置き換えは可能ですが、ドメインの名前で包む自作を残しました。
- 交換律は成り立たず、順番が結果を変えることをテストにしました。
- `sbt test` は 30 本が通ります。

次章では、命令を実行して出来事を生成します。出来事の列が正しいかをコマンドが検証し、状態遷移を `enum` で表します。

## この章で書いたコード

- `apps/scala/zettai/src/main/scala/zettai/Events.scala`
- `apps/scala/zettai/src/main/scala/zettai/Replay.scala`
- `apps/scala/zettai/src/main/scala/zettai/StateTransition.scala`
- `apps/scala/zettai/src/test/scala/zettai/Gen.scala`
- `apps/scala/zettai/src/test/scala/zettai/EventsSuite.scala`
- `apps/scala/zettai/src/test/scala/zettai/ReplaySuite.scala`
- `apps/scala/zettai/src/test/scala/zettai/TransitionSuite.scala`

## 参照

- [Cats の Monoid](https://typelevel.org/cats/typeclasses/semigroup.html)
- [Cats の MonoidK](https://typelevel.org/cats/typeclasses/semigroupk.html)
