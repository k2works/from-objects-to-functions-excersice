---
type: Article
title: "第 10 章 コンテキストを読み込み、コマンドを処理する"
description: "Zettai 連載 Scala 版の第 10 章。コマンドの処理が必要とする文脈を、Kleisli と IOLocal で持ち回して比べ、Kleisli を採る。複数のコマンドを 1 つの単位として処理するバッチと、データベースに対する射影のクエリを確かめる。"
tags: [article, zettai, scala, cats-effect, kleisli, iolocal, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-02T14:00:00Z }
---

# 第 10 章 コンテキストを読み込み、コマンドを処理する

第 4 章で、依存の渡し方を 3 通り書いて比べ、`Kleisli` は「第 10 章で再検討する」と残しました。この章で、その続きを書きます。コマンドの処理が必要とする **文脈**（イベントストア）を、引数ではなく環境として持ち回します。

## モナドでデータベースにアクセスする

第 9 章の `PostgresStore` は、`Transactor` を通して接続を開き、トランザクションを張りました。ここで、その性質を整理します。

- `ConnectionIO` は、接続を使う処理の記述で、`transact` したときに初めて接続を開き、処理して閉じる
- `Transactor.fromDriverManager` は、`transact` のたびに接続を開く。接続プールは使っていない
- `append` の中で、版の確認と挿入は 1 つのトランザクションになる

一方、コマンドの処理は `load` と `append` を別々に呼びます。**読む、判断する、書くは、1 つのトランザクションではありません。**

```scala
  def process(command: ToDoListCommand): Context[Either[ZettaiError, Unit]] =
    Kleisli { store =>
      store.load.flatMap { read =>
        val state = Replay.replayFrom(Events.empty, read.events)
        Commands.handle(command, state) match
          case Right(events) => store.append(read.version, events)
          case Left(error)   => IO.pure(Left(error))
      }
    }
```

間に別の書き手が割り込んでも、`append` が読んだ版を添えて版の確認をするので、古い判断に基づく書き込みは `VersionConflict` で断られます。トランザクションで囲む代わりに、**版で守っている**のが、このイベントストアの形です。

## ContextReader を使用したコマンドの処理

`process` は、`EventStore` を引数に取りません。型 `Kleisli[IO, EventStore, A]` が「`EventStore` を与えられたら `IO[A]` を返す処理」を表します。

```scala
  type Context[A] = Kleisli[IO, EventStore, A]
```

文脈は引数に現れず、型にだけ現れます。実行するときに、`run` で文脈を渡します。

### 文脈を引数で渡す版と同じ結果になる

第 6 章の `CommandHub` と、この章の `process` に、同じコマンドの列を流します。拒否される 1 件を含めています。

```scala
  test("Kleisli: 文脈として渡したストアに、コマンドの入口と同じ結果が出る") {
    for
      a      <- EventStore.inMemory
      b      <- EventStore.inMemory
      viaHub <- commands.traverse(CommandHub.commandHub(a).execute)
      viaCtx <- commands.traverse(Process.process(_).run(b))
      readA  <- a.load
      readB  <- b.load
    yield
      assertEquals(viaCtx, viaHub)
      assertEquals(readB, readA)
  }
```

結果の列も、書かれたイベントも一致しました。同じ処理を、別のストアに差し替えて実行することもできます。

```scala
  test("Kleisli: 同じ処理を、別のストアに差し替えて実行できる") {
    for
      a <- EventStore.inMemory
      b <- EventStore.inMemory
      program = Process.process(CreateToDoList(uberto, book))
      _     <- program.run(a)
```

### IOLocal で文脈を持ち回す

もう 1 つの手段が `IOLocal` です。ファイバー（`IO` の軽量スレッド）ごとの変数で、設定しておけば、引数も型も使わずに、どこからでも読めます。

```scala
  private def processWithLocal(local: IOLocal[Option[EventStore]], command: ToDoListCommand) =
    local.get.flatMap {
      case Some(store) => Process.process(command).run(store)
      case None        => IO.raiseError(new IllegalStateException("文脈（EventStore）が設定されていません"))
    }
```

設定した文脈は、引数なしの処理が読みます。

```scala
  test("IOLocal: 設定した文脈を、引数なしの処理が読む") {
    for
      store <- EventStore.inMemory
      local <- IOLocal[Option[EventStore]](None)
      _     <- local.set(Some(store))
      done  <- processWithLocal(local, CreateToDoList(uberto, book))
      read  <- store.load
    yield
      assertEquals(done, Right(()))
      assertEquals(read.version, 1)
  }
```

ただし、**設定し忘れは型に現れません。** 実行して初めて失敗します。

```scala
  test("IOLocal: 文脈の設定し忘れは型に現れず、実行時に失敗する") {
    for
      local   <- IOLocal[Option[EventStore]](None)
      outcome <- processWithLocal(local, CreateToDoList(uberto, book)).attempt
    yield assertEquals(outcome.left.map(_.getMessage), Left("文脈（EventStore）が設定されていません"))
  }
```

子ファイバーを起動したときの振る舞いも確かめました。子は、起動した時点の親の値を引き継ぎます。子が変更しても、親の値は変わりません。

```scala
  test("IOLocal: 子ファイバーは親の値を引き継ぎ、子の変更は親に戻らない") {
```

### どちらを採るか

| 観点 | 引数（`Hub` など） | `Kleisli` | `IOLocal` |
| :--- | :--- | :--- | :--- |
| 文脈の見え方 | 引数に現れる | 型に現れる | どこにも現れない |
| 渡し忘れ | コンパイルエラー | `run` を呼ばないと動かない | **実行時に失敗する** |
| 差し替え | 別の引数を渡す | `run` に別の文脈を渡す | `set` で書き換える（可変） |
| 子ファイバー | 関係しない | 関係しない | 起動時点の値を引き継ぐ |
| 読み手に要る知識 | 少ない | `Kleisli` | ファイバーの局所変数 |

**この連載では、`Kleisli` を採ります。** 文脈が型に現れ、渡し忘れが実行前に分かるためです。`IOLocal` は、型にも引数にも現れない代わりに、設定し忘れが実行時にしか分かりません。この題材で、その引き換えに見合うものはありません。

ログの相関 ID のように、**あらゆる処理に流れ込み、業務の判断には使わない情報**は、`IOLocal` が向くかもしれません。第 12 章で、その使い道を検討します。

## トランザクションの境界を決める

複数のコマンドを、1 つの単位として処理したい場面があります。途中で拒否されたら、それまでに受け入れられた分も、なかったことにしたいのです。

```scala
  def processAll(commands: List[ToDoListCommand]): Context[Either[ZettaiError, Unit]] =
```

あとのコマンドは、前のコマンドが作った状態を見ます。すべて受け入れられたら、1 回の `append` でまとめて書きます。

```scala
        val start: Either[ZettaiError, (ToDoListState, List[ToDoListEvent])] =
          Right((Replay.replayFrom(Events.empty, read.events), Nil))
        val decided = commands.foldLeft(start) { (acc, command) =>
          acc.flatMap { (state, written) =>
            Commands.handle(command, state).map(events => (Replay.replayFrom(state, events), written ::: events))
          }
        }
```

```scala
        decided match
          case Right((_, events)) => store.append(read.version, events)
          case Left(error)        => IO.pure(Left(error))
```

1 回の `append` が、版の確認と挿入を 1 つのトランザクションにするので、**書き込みはすべてか、何もないか**です。

```scala
  test("バッチ: すべて成功すると、1 回の書き込みで版がイベントの数だけ進む") {
```

### 中のコマンドが 1 つでも拒否されたら

3 つ目のコマンドが拒否されるバッチを流します。

```scala
  test("バッチ: 1 つでも拒否されたら、成功していたコマンドの分も書かれない") {
    val batch = List(
      CreateToDoList(uberto, book),
      AddToDoItem(uberto, book, ToDoItem.todo("a")),
      AddToDoItem(uberto, ListName("missing"), ToDoItem.todo("b"))
    )
```

```scala
      assertEquals(result, Left(ZettaiError.ListNotFound(uberto, ListName("missing"))))
      assertEquals(read, Versioned(0, Nil))
```

先の 2 つのコマンドは受け入れられていたのに、ストアには何も書かれていません。あとのコマンドが前のコマンドの結果を見ることも確かめました。

```scala
  test("バッチ: 前のコマンドが作った状態を、あとのコマンドが見る") {
```

## 設計で確かめたこと

Flix 版は、この章で設計の見立てを 2 回直しました。Scala 版では、設計を間違えたと言える出来事が、この章では起きませんでした。代わりに、確かめた判断を書きます。

- **文脈は型に載せる。** `IOLocal` は設定し忘れが実行時にしか分からないことを、テストで確かめた
- **トランザクションの代わりに版で守る。** `load` と `append` を別々に呼んでも、古い判断の書き込みは第 9 章の `VersionConflict` が断る
- **バッチは、判断を先に済ませて、書き込みを 1 回にする。** 書き込みの途中に判断を挟まないので、部分的に書かれる状態がない

`processAll` の途中で、別の書き手が書いた場合も確かめました。読み出しのあと、書き込みの直前に別の書き手が割り込むストアを作り、バッチを流します。結果は `VersionConflict` で、バッチの分は何も書かれず、割り込んだ書き手のイベントだけが残ります。

```scala
  test("バッチ: 読んだあとに別の書き手が書いていたら、全体が VersionConflict で断られ、バッチの分は書かれない") {
```

版の確認を壊すと、このテストを含む 4 本が落ちました。

## データベースに対する射影のクエリ

第 8 章の `QueryHub` は、`EventStore` を受け取るだけでした。ストアが PostgreSQL に変わっても、そのまま動くはずです。

```scala
  test("データベースのイベントから作った行は、インメモリで作った行と同じ") {
```

同じコマンドの列を、PostgreSQL のストアとインメモリのストアに流し、それぞれ `QueryHub` で行を読みました。

```scala
      assertEquals(dbRows, List(ToDoListRow(book, 2, 1)))
      assertEquals(dbRows, memRows)
```

行の数は 2 項目で、そのうち 1 つが完了です。データベースの経路でも、インメモリの経路と同じ行になりました。**読む側のコードは、1 文字も変えていません。**

## イベントソーシングによるドメインのモデリング

ここまでで、イベントソーシングの部品がそろいました。

| 部品 | 役割 | 章 |
| :--- | :--- | :--- |
| `ToDoListEvent` | すでに起きた事実 | 5 |
| `ToDoListCommand` | これから行いたい意図。拒否できる | 6 |
| `Commands.handle` | 状態とコマンドから、イベントか拒否の理由を決める純粋関数 | 6 |
| `EventStore` | イベントの置き場所。版つきで書き足す | 6・9 |
| `Projection` | イベントを画面向けの行に畳み込む | 8 |
| `Process` | 文脈を環境にして、コマンドを処理する | 10 |

状態は保存しません。イベントから、必要なときに畳み込みます。保存先が変わっても、判断（`handle`）と読み出し（`Projection`）は変わりませんでした。

## まとめ

この章では、コマンドの処理が必要とする文脈を持ち回し、次を確認しました。

- `Kleisli` で文脈を型に載せると、引数で渡す版と同じ結果になり、別のストアにも差し替えられる
- `IOLocal` は、設定し忘れが型に現れず実行時に失敗する。子ファイバーは親の値を引き継ぐ。この連載は `Kleisli` を採る
- 読む、判断する、書くは 1 つのトランザクションではない。版で守る
- 複数のコマンドは、判断を先に済ませて 1 回で書けば、すべてか何もないかになる
- 読む側のコードは、ストアが PostgreSQL になっても変わらない

`sbt test` は 90 本が通ります（第 9 章の 81 本に、9 本を足しました）。公開後に、バッチの競合のテストを 1 本足したので、最終は 131 本です。

次章では、リスト名の変更を足します。入力の誤りが複数あるとき、最初の 1 つで止めずに、すべて集めて返します。

## この章で書いたコード

- `apps/scala/zettai/src/main/scala/zettai/Process.scala`
- `apps/scala/zettai/src/test/scala/zettai/ProcessSuite.scala`
- `apps/scala/zettai/src/test/scala/zettai/QueryOnDatabaseSuite.scala`

## 参照

- [Cats の Kleisli](https://typelevel.org/cats/datatypes/kleisli.html)
- [Cats Effect の IOLocal](https://typelevel.org/cats-effect/docs/core/io-local)
