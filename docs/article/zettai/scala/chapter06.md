---
type: Article
title: "第 6 章 コマンドを実行してイベントを生成する"
description: "Zettai 連載 Scala 版の第 6 章。コマンドを enum で表し、状態とコマンドからイベントか拒否の理由を返す純粋関数を Either で書く。enum にケースを足すと match がコンパイルエラーになることを確かめ、Ref を使うイベントストアとつなぐ。"
tags: [article, zettai, scala, cats-effect, state-machine, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-02T23:30:00Z }
---

# 第 6 章 コマンドを実行してイベントを生成する

この章では、利用者の意図を **コマンド** として表し、現在の状態から **イベントか拒否の理由** を決める純粋関数を作ります。状態遷移の規則は `enum` と `match` で書き、足りないケースはコンパイラに見つけさせます。

## 新しい ToDo リストの作成

最初の要求は、新しいリストを作ることです。同じ名前のリストがすでにあれば拒否しなければなりません。項目の追加と、項目の状態変更も同じ入口に集めます。この章で作るのはドメインとアプリケーションの部分で、公開済みの HTTP の入口はリストを読む GET だけのままです。

できる操作だけでなく、次の拒否もテストから書きました。

- 既存リストの再作成
- 存在しないリストへの項目追加
- 存在しない項目の状態変更
- 許されない状態遷移

成功例だけなら、すべての操作を許す実装でも Green になります。拒否例は、ドメインの境界を決めるテストです。

## コマンドを使って状態を変更する

利用者の意図を、閉じた型で表します。

```scala
enum ToDoListCommand:
  case CreateToDoList(user: User, name: ListName)
  case AddToDoItem(user: User, name: ListName, item: ToDoItem)
  case ChangeItemStatus(user: User, name: ListName, description: String, status: ToDoStatus)
```

コマンドとイベントは似ていますが、時制と責務が違います。

| | コマンド | イベント |
| :--- | :--- | :--- |
| 意味 | これから行いたい意図 | すでに起きた事実 |
| 名前 | `CreateToDoList` | `ListCreated` |
| 拒否 | できる | しない |
| 保存 | 原則として保存しない | 履歴として保存する |

コマンドは検証され、成功したときだけ 1 件のイベントになります。イベントは再生の入力なので、適用するときに同じ業務判断を繰り返しません。第 5 章の `apply` が、作成されていないリストへの追加を黙って無視したのはそのためです。拒否はコマンドの仕事で、イベントの仕事ではありません。

拒否には理由が要ります。理由は文字列ではなく、判断に使った値を持つ型にしました。

```scala
enum ZettaiError:
  case ListNotFound(user: User, name: ListName)
  case ListAlreadyExists(user: User, name: ListName)
  case ItemNotFound(user: User, name: ListName, description: String)
  case InvalidTransition(description: String, from: ToDoStatus, to: ToDoStatus)
```

## 状態とイベントによるドメインのモデリング

項目の状態変更を、新しいイベントとして足します。

```scala
  case ItemStatusChanged(user: User, name: ListName, description: String, status: ToDoStatus)
```

`enum` にケースを足しただけで、`Events.apply` の `match` が足りなくなりました。`-Werror` の下では、コンパイルが止まります（パスは省略しています）。

```bash
[error] -- [E029] Pattern Match Exhaustivity Error: src/main/scala/zettai/Events.scala:24:4
[error] 24 |    event match
[error]    |    ^^^^^
[error]    |match may not be exhaustive.
[error]    |
[error]    |It would fail on pattern case: zettai.ToDoListEvent.ItemStatusChanged(_, _, _, _)
```

**新しいイベントを足したとき、適用の漏れをテストではなくコンパイラが見つけます。** 先回りして実装せずに、コンパイラの指摘に従って足します。

```scala
      case ToDoListEvent.ItemStatusChanged(user, name, description, status) =>
        listFor(state, user, name) match
          case Some(ToDoList(_, items)) =>
            put(state, user, name, ToDoList(name, Domain.changeStatus(items, description, status)))
          case None => state
```

状態の変更は、説明が最初に一致した項目だけに適用します。

```scala
  def changeStatus(items: List[ToDoItem], description: String, status: ToDoStatus): List[ToDoItem] =
    items.indexWhere(_.description == description) match
      case -1    => items
      case index => items.updated(index, items(index).copy(status = status))
```

許される状態遷移は、表にします。

```scala
  def canTransition(current: ToDoStatus, next: ToDoStatus): Boolean =
    (current, next) match
      case (ToDoStatus.Todo, ToDoStatus.InProgress)    => true
      case (ToDoStatus.Todo, ToDoStatus.Done)          => true
      case (ToDoStatus.InProgress, ToDoStatus.Done)    => true
      case (ToDoStatus.InProgress, ToDoStatus.Blocked) => true
      case (ToDoStatus.Blocked, ToDoStatus.InProgress) => true
      case _                                           => false
```

`Done` からは戻せません。`Todo` から `Blocked` へも直接は移せません。最後の `case _` は、表に載せない組み合わせをすべて拒否します。**許す遷移を列挙し、残りを拒否する形です。**

```scala
  test("Done からは戻せない") {
    assertEquals(
      ToDoStatus.values.toList.filter(next => Domain.canTransition(ToDoStatus.Done, next)),
      Nil
    )
  }
```

## 関数型ステートマシンを記述する

関数型ステートマシンの中心は、コマンドと現在状態を受け取って、イベントまたは拒否の理由を返す純粋関数です。

```scala
  def handle(command: ToDoListCommand, state: ToDoListState): Either[ZettaiError, List[ToDoListEvent]] =
    command match
      case ToDoListCommand.CreateToDoList(user, name) =>
        Events.listFor(state, user, name) match
          case Some(_) => Left(ZettaiError.ListAlreadyExists(user, name))
          case None    => Right(List(ToDoListEvent.ListCreated(user, name)))
      case ToDoListCommand.AddToDoItem(user, name, item) =>
        for _ <- requireList(state, user, name)
        yield List(ToDoListEvent.ItemAdded(user, name, item))
      case ToDoListCommand.ChangeItemStatus(user, name, description, next) =>
        for
          list    <- requireList(state, user, name)
          item    <- requireItem(user, name, description, list)
          allowed <- checkTransition(description, item, next)
        yield List(ToDoListEvent.ItemStatusChanged(user, name, allowed, next))
```

`for` は `Either` の連鎖を上から下へ読める形で書けます。どれかが `Left` を返した時点で、後続は実行されず、その `Left` がそのまま結果になります。`Either` は Scala の標準にあり、`flatMap` も最初から持っています。

各ステップは、見つからなければ理由を持つ `Left` にします。

```scala
  private def requireList(state: ToDoListState, user: User, name: ListName): Either[ZettaiError, ToDoList] =
    Events.listFor(state, user, name).toRight(ZettaiError.ListNotFound(user, name))
```

```scala
  private def checkTransition(description: String, item: ToDoItem, next: ToDoStatus): Either[ZettaiError, String] =
    if Domain.canTransition(item.status, next) then Right(description)
    else Left(ZettaiError.InvalidTransition(description, item.status, next))
```

`handle` は現在状態を引数に取るだけで、保存先を知りません。同じコマンドと同じ状態からは、常に同じ結果が返ります。

テストは拒否の理由を、構造ごと比べます。

```scala
  test("遷移表が許さない遷移は拒否する") {
    assertEquals(
      Commands.handle(ChangeItemStatus(uberto, book, "write chapter", ToDoStatus.Blocked), withItem),
      Left(ZettaiError.InvalidTransition("write chapter", ToDoStatus.Todo, ToDoStatus.Blocked))
    )
  }
```

## ハブと接続する

`handle` を実際に使うには、過去のイベントを読み、現在状態を作り、コマンドを実行し、新しいイベントを書き足す、という手順が要ります。保存先との出入りを、関数を持つ値にまとめます。

```scala
final case class EventStore(
    load: IO[List[ToDoListEvent]],
    append: List[ToDoListEvent] => IO[Unit]
)
```

インメモリの実装は、`Ref` を使います。`Ref` は書き換えられる箱で、Cats Effect では操作が `IO` になります。

```scala
  def inMemory: IO[EventStore] =
    Ref.of[IO, List[ToDoListEvent]](Nil).map { history =>
      EventStore(history.get, events => history.update(_ ::: events))
    }
```

Flix 版では、可変な箱は領域（`region`）の中にだけ存在でき、持ち出すとコンパイルが止まりました。Scala には領域がありません。その代わり、**箱を作ること自体を `IO` にしました。** 戻り値が `IO[EventStore]` なので、ストアを作る手順は、実行するまで何も起きません。

コマンドの入口は、このストアの上に作ります。

```scala
  def commandHub(store: EventStore): CommandHub =
    val state: IO[ToDoListState] = store.load.map(Replay.replayFrom(Events.empty, _))
    CommandHub(
      command =>
        state.flatMap { current =>
          Commands.handle(command, current) match
            case Right(events) => store.append(events).as(Right(()))
            case Left(error)   => IO.pure(Left(error))
        },
      state
    )
```

拒否されたときは、何も書き足しません。

```scala
  test("拒否されたコマンドは、何も書き足さない") {
    withHub { (store, commands) =>
      for
        rejected <- commands.execute(AddToDoItem(uberto, book, ToDoItem.todo("write chapter")))
        events   <- store.load
      yield
        assertEquals(rejected, Left(ZettaiError.ListNotFound(uberto, book)))
        assertEquals(events, Nil)
    }
  }
```

ここでの `execute` は、読んで、判断して、書く、の 3 手順が分かれています。2 つのコマンドが同時に走ると、同じ状態を読んで両方が受け入れられる余地があります。この問題は、第 9 章で版の確認を入れて扱います。

## コマンドとイベントを深く知る

個別のテストは、思いついた場合しか確かめません。**「受け入れられたコマンドは、必ず状態を変える」** という性質を、小さな範囲のすべてのコマンド列で確かめます。

```scala
  test("受け入れられたコマンドは、必ず状態を変える") {
    for length <- 0 to 3; sequence <- Gen.ofLength(length, commands) do
      for (before, after) <- accepted(sequence) do assertNotEquals(after, before)
  }
```

コマンドは 8 種類（2 つのリスト作成、2 つの項目追加、4 つの状態変更）で、長さ 3 までの 585 通りの列を試します。

### 壊して確かめる

この性質のテストが、壊れた実装を見分けられるかを確かめました。遷移表の最後の `case _` を `true` に書き換えて、**すべての遷移を許す**ようにします。

<!-- code-check: ignore わざと壊したコード -->
```scala
      case _                                           => true
```

すると、同じ状態への変更（例えば `Todo` を `Todo` に変える）が受け入れられ、状態が変わらないイベントが生まれます。3 本が落ちました。

```bash
==> X zettai.CommandInvariantsSuite.受け入れられたコマンドは、必ず状態を変える
==> X zettai.CommandsSuite.遷移表が許さない遷移は拒否する
==> X zettai.CommandsSuite.Done からは戻せない
```

個別のテスト 2 本に加えて、性質のテストも落ちました。元に戻すと、すべて通ります。

## まとめ

この章では、コマンドからイベントを決めるステートマシンを作り、次を確認しました。

- コマンドは意図で拒否でき、イベントは事実で拒否しない
- 拒否の理由は、判断に使った値を持つ `enum` で表す
- `enum` にケースを足すと、`match` の漏れを `-Werror` の下でコンパイラが指摘する
- `handle` は `Either` の `for` で書ける純粋関数で、保存先を知らない
- 可変な `Ref` は、作ることが `IO` になる。領域のような仕組みは要らない
- 「受け入れられたコマンドは必ず状態を変える」を、585 通りの列で確かめた。遷移表を壊すと落ちる
- 読んで判断して書く 3 手順は同時実行に弱い。第 9 章で扱う

`sbt test` は 43 本が通ります（第 5 章の 30 本に、13 本を足しました）。

次章では、エラーを運ぶ手段を `Either` と `IO` の失敗で比べ、HTTP の入口まで通します。

## この章で書いたコード

- `apps/scala/zettai/src/main/scala/zettai/Domain.scala`
- `apps/scala/zettai/src/main/scala/zettai/Events.scala`
- `apps/scala/zettai/src/main/scala/zettai/Errors.scala`
- `apps/scala/zettai/src/main/scala/zettai/Commands.scala`
- `apps/scala/zettai/src/main/scala/zettai/EventStore.scala`
- `apps/scala/zettai/src/main/scala/zettai/CommandHub.scala`
- `apps/scala/zettai/src/test/scala/zettai/CommandsSuite.scala`
- `apps/scala/zettai/src/test/scala/zettai/CommandHubSuite.scala`
- `apps/scala/zettai/src/test/scala/zettai/CommandInvariantsSuite.scala`

## 参照

- [Scala 3 の enum](https://docs.scala-lang.org/scala3/book/types-adts-gadts.html)
- [Cats Effect の Ref](https://typelevel.org/cats-effect/docs/std/ref)
