---
type: Article
title: "第 9 章 モナドによる安全なデータ永続化"
description: "Zettai 連載 Scala 版の第 9 章。イベントストアに版を持たせ、doobie の ConnectionIO で PostgreSQL に永続化する。Testcontainers で結合テストを書き、モナドの法則とトランザクションの原子性、想定外の失敗を 500 にして理由を出さないことを確かめる。"
tags: [article, zettai, scala, cats-effect, doobie, postgresql, testcontainers, monad, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-02T13:30:00Z }
---

# 第 9 章 モナドによる安全なデータ永続化

この章では、イベントストアを PostgreSQL に置き換えます。使うのは doobie の `ConnectionIO` で、複数の SQL を **1 つのトランザクション** に合成します。テストは Testcontainers で、テスト自身が PostgreSQL を起動します。

## 安全に永続化する

永続化で起きうる失敗を、先に数えます。

| 失敗 | 例 |
| :--- | :--- |
| 接続できない | 接続先が停止している、ポートが違う |
| SQL が失敗する | テーブルが無い、制約に違反する |
| 読んだ行が読めない | 知らない種類のイベント、知らない状態の名前 |
| 同時に書かれた | 他の書き手が、先に同じ版を書いた |
| 外から来た値が危ない | テーブル名に SQL が混ざる |

最後から 2 つ目は、**業務の失敗**です。呼び出し側が「やり直す」「利用者に伝える」と場合分けできます。第 6 章の読んで・判断して・書くの 3 手順が、同時に走ったときの問題をここで扱います。

```scala
  case VersionConflict(expected: Int, actual: Int)
```

残りは**想定外の失敗**です。[ADR-050](../../../adr/ADR-050-scala-either-for-domain-failures.md) の分類に従って、`IO` の失敗にします。`ZettaiError` に `StorageFailure` は足しません。

ケースを足すと、写像の `match` がコンパイルエラーで更新漏れを教えてくれます。競合は、利用者の誤りではなく、状態が先へ進んだことなので、409 に写しました。

```scala
      case ZettaiError.VersionConflict(_, _)      => 409
```

## PostgreSQL と結合テスト

データベースを使うテストは、他のテストと性質が違います。外部のプロセスが要り、時間がかかり、前のテストの残りに影響されます。方針は 3 つです。

- テストが自分で PostgreSQL を起動する。**Testcontainers** を使う
- テストごとに**専用のテーブル**を作り、終わったら捨てる。共有のテーブルに書くと、テストの順番に結果が左右される
- CI でも同じ仕組みを使う。GitHub Actions のランナーには Docker があるため、追加の設定は要らない見込み（CI で確かめる）

Flix 版は、環境変数で接続先を決め、docker-compose と CI のサービスコンテナを使いました。この PC では 5432 番を別のプロジェクトのコンテナが使っていて、別のポートを探す手間がありました。Testcontainers は、空いているポートを自分で選ぶので、その衝突が起きません。

起動は、テストの最初に 1 度だけ行います。

```scala
object TestDatabase:

  private lazy val container: PostgreSQLContainer =
    val started = new PostgreSQLContainer("postgres:17-alpine")
    started.start()
    started
```

```scala
  lazy val config: PostgresStore.Config =
    PostgresStore.Config(container.getJdbcUrl, container.getUsername, container.getPassword)
```

コンテナは、最初に使われたときに起動し、JVM が終わると Testcontainers が片付けます。スイートごとに起動すると遅いので、全スイートで共有しました。

## Scala でデータベースに接続する

依存は `build.sbt` に書きます。doobie は 1.0.0-RC13 です。安定版の 1.0 は、まだ Maven Central にありません。

```scala
      "org.typelevel" %% "doobie-core"         % "1.0.0-RC13",
      "org.typelevel" %% "doobie-postgres"     % "1.0.0-RC13",
      "org.testcontainers" % "testcontainers-postgresql" % "2.0.5" % Test,
```

つまずきとして、`import doobie.*` はコンパイルが通りませんでした。RC13 では、パッケージ名が `org.typelevel.doobie` に変わっています。それ以前の RC12 は groupId が `org.tpolecat` で、パッケージは `doobie` でした。**RC 版を使うと、版が変わるたびにこういう変更を取り込む必要があります。** 判断は [ADR-051](../../../adr/ADR-051-scala-doobie-and-testcontainers.md) に記録しました。

接続は `Transactor` が担います。

```scala
  def transactor(config: Config): Transactor[IO] =
    Transactor.fromDriverManager[IO]("org.postgresql.Driver", config.url, config.user, config.password, None)
```

`Transactor` は、`ConnectionIO`（接続を使う処理の記述）を `IO` に変えます。接続を開いて、閉じるまでを担うので、後始末の書き忘れがありません。接続プールは使っていません。トランザクションごとに接続を開きます。

### 効果の観察

`ConnectionIO[A]` は、**まだ実行されていない、接続を使う処理の記述**です。`IO` と同じく、作っただけでは何も起きません。実行するのは `transact` で、そのとき初めて接続を開き、処理して、閉じます。

### 例外を値にする

SQL の例外は、そのまま `IO` の失敗として流れます。業務の失敗である版の競合だけを `Either` の値にします。

## データベースを準備する

表は、1 行が 1 件のイベントです。

```scala
        fr"create table if not exists" ++ t ++
          fr"(version int primary key, kind text not null, user_name text not null, list_name text not null, description text not null, status text not null)"
```

`version` が主キーです。**同じ版を 2 つの書き手が書こうとすると、データベースの一意制約が 2 つ目を止めます。**

テーブル名は、SQL に組み込むため、英小文字・数字・下線だけを許します。

```scala
  def validTableName(name: String): Boolean =
    name.nonEmpty && name.forall(c => c.isLower || c.isDigit || c == '_')
```

```scala
    else IO.raiseError(new IllegalArgumentException("テーブル名に使えない文字が含まれています"))
```

`; drop table users` のような名前は、SQL を組み立てる前に断ります。

```scala
  test("危ないテーブル名は、SQL を組み立てる前に断る") {
    xa.flatMap { transactor =>
      PostgresStore.open(transactor, "events; drop table users").attempt.map { outcome =>
        assert(outcome.isLeft)
        assertEquals(PostgresStore.validTableName("events; drop table users"), false)
        assertEquals(PostgresStore.validTableName("events_abc_123"), true)
      }
    }
  }
```

## 関数型スタイルでリモートデータにアクセスする

ストアの形は、第 6 章から変えていません。`EventStore` は、読み出しと書き足しの 2 つの関数を持つ値です。変わったのは、**読んだ版を添えて書き足す**ことと、戻り値が `Either` になったことです。

```scala
final case class Versioned(version: Int, events: List[ToDoListEvent])
```

```scala
final case class EventStore(
    load: IO[Versioned],
```

インメモリの実装は、`Ref` の `modify` で、版の確認と書き足しを 1 つの操作にします。

```scala
          history.modify { current =>
            if current.version != expected then (current, Left(ZettaiError.VersionConflict(expected, current.version)))
            else (Versioned(current.version + events.size, current.events ::: events), Right(()))
          }
```

PostgreSQL の実装は、同じ形を `ConnectionIO` で書きます。

```scala
      val currentVersion: ConnectionIO[Int] =
        (fr"select coalesce(max(version), 0) from" ++ t).query[Int].unique
```

```scala
      def insert(rows: List[Row]): ConnectionIO[Int] =
        Update[Row](
          s"insert into $table (version, kind, user_name, list_name, description, status) values (?, ?, ?, ?, ?, ?)"
        ).updateMany(rows)
```

## モナドの力を探る

`ConnectionIO` はモナドです。`flatMap` で、前の結果を使って次の処理を決められます。版の確認と挿入を、1 つのトランザクションにまとめます。

```scala
      def appendAt(expected: Int, events: List[ToDoListEvent]): ConnectionIO[Either[ZettaiError, Unit]] =
        currentVersion.flatMap { actual =>
          if actual != expected then (Left(ZettaiError.VersionConflict(expected, actual)): Either[ZettaiError, Unit]).pure[ConnectionIO]
          else insert(events.zipWithIndex.map((e, i) => encode(expected + i + 1, e))).as(Right(()))
        }
```

確認と挿入の間に、別の書き手が割り込む余地は残ります。そのとき止めるのが、主キーの一意制約です。SQL の状態コード `23505`（一意制約違反）を受け取って、競合の値に変えます。

```scala
          case e: SQLException if e.getSQLState == "23505" =>
            currentVersion.transact(xa).map(actual => Left(ZettaiError.VersionConflict(expected, actual)))
```

### モナドの法則

`ConnectionIO` がモナドの法則を満たすことを、実際の PostgreSQL に対して確かめました。値は 3 つずつです。

```scala
  test("モナド則: 左単位元") {
    List(0, 1, Int.MaxValue - 1).traverse_ { a =>
      for
        left  <- a.pure[ConnectionIO].flatMap(plusOne).transact(xa)
        right <- plusOne(a).transact(xa)
      yield assertEquals(left, right)
    }
  }
```

```scala
  test("モナド則: 右単位元") {
    List(0, 1, 41).traverse_ { a =>
      for
        left  <- plusOne(a).flatMap(_.pure[ConnectionIO]).transact(xa)
        right <- plusOne(a).transact(xa)
      yield assertEquals(left, right)
    }
  }
```

```scala
  test("モナド則: 結合律") {
    List(0, 1, 41).traverse_ { a =>
      for
        left  <- plusOne(a).flatMap(plusOne).flatMap(timesTwo).transact(xa)
        right <- plusOne(a).flatMap(n => plusOne(n).flatMap(timesTwo)).transact(xa)
      yield assertEquals(left, right)
    }
  }
```

3 つの値だけでは、法則が成り立つことの証明にはなりません。確かめたのは、**この範囲に反例がない**ことです。

### トランザクションの原子性

モナドの合成が、安全につながる場面があります。途中で失敗すると、同じトランザクションの前の書き込みが取り消されます。

```scala
      boom   = (fr"insert into" ++ t ++ fr"values (1)").update.run *>
                 FC.raiseError[Int](new RuntimeException("途中の失敗"))
```

```scala
    yield
      assert(failed.isLeft)
      assertEquals(count, 0)
```

挿入の後に失敗させても、表には 1 行も残りません。

### 同時に書く

2 つの接続が、同じ版から同時に書きます。

```scala
  test("2 つの接続が同じ版から同時に書くと、ちょうど一方だけが成功し、負けた側は何も残さない") {
```

ちょうど一方だけが成功し、読み戻したイベントの数は、版と一致します。負けた側の書き込みは、何も残りません。

## 読み戻す

書いたイベントを、同じ順序で読み戻せることを確かめます。別の接続からも見えます。

```scala
  test("書いたイベントを、同じ順序で読み戻せる") {
    withTable { (open, _) =>
      for
        store <- open
        _     <- store.append(0, List(created, added))
        _     <- store.append(2, List(changed))
        read  <- store.load
      yield assertEquals(read, Versioned(3, List(created, added, changed)))
    }
  }
```

項目の説明に `a & <b>` のような特殊文字を入れてあります。保存と読み戻しで壊れません。

古い版で書こうとすると、断られ、何も書かれません。

```scala
  test("古い版で書こうとすると断られ、何も書かれない") {
    withTable { (open, _) =>
      for
        store    <- open
        _        <- store.append(0, List(created))
        rejected <- store.append(0, List(added))
        read     <- store.load
      yield
        assertEquals(rejected, Left(ZettaiError.VersionConflict(0, 1)))
        assertEquals(read, Versioned(1, List(created)))
    }
  }
```

### 想定外の失敗は 500 にして、理由を出さない

保存の失敗の理由には、接続先や SQL が入りえます。そのまま応答の本文に出すと、内部の情報が漏れます。Flix 版では、公開後にこの穴を見つけて直しました。Scala 版は、最初から塞ぎます。

```scala
          .handleErrorWith(_ => InternalServerError("Internal Server Error"))
```

テストは、理由に秘密の文字列を入れた失敗を起こして、本文に出ないことを確かめます。

```scala
  private val secret = "jdbc:postgresql://db.internal:5432/zettai password=hunter2"
```

```scala
      assertEquals(response.status, Status.InternalServerError)
      assertEquals(body, "Internal Server Error")
      assert(!body.contains("hunter2"))
```

## ３経路目

第 3 章の受け入れテストに、3 経路目を足します。データベースの経路です。

```scala
  def databaseActions(lists: Map[User, List[ToDoList]]): Resource[IO, ZettaiActions] =
    val xa = PostgresStore.transactor(TestDatabase.config)
    for
      table <- Resource.eval(PostgresStore.freshTableName)
      store <- Resource.make(PostgresStore.open(xa, table))(_ => PostgresStore.dropTable(xa, table))
      _     <- Resource.eval(store.append(0, eventsOf(lists)))
    yield
```

`Resource` で、専用のテーブルを作って、使い終わったら捨てます。シナリオは 1 文字も変えていません。

```scala
  private def onEveryRoute(scenario: ZettaiActions => IO[Unit]): IO[Unit] =
    Routes.onEveryRoute(lists)(scenario) *> Routes.databaseActions(lists).use(scenario)
```

ドメイン、HTTP、データベースの 3 経路で、同じシナリオが通りました。HTTP からデータベースまで通して、コマンドで書いたものを HTTP で読めることも確かめました。

```scala
        stale   <- commands.executeAt(0, AddToDoItem(uberto, book, ToDoItem.todo("late")))
```

```scala
        assertEquals(stale, Left(ZettaiError.VersionConflict(0, 2)))
```

## まとめ

この章では、イベントストアを PostgreSQL に置き換え、次を確認しました。

- 業務の失敗（版の競合）は `Either` で、想定外の失敗（接続切れなど）は `IO` の失敗で運ぶ
- `ConnectionIO` はモナドで、複数の SQL を 1 つのトランザクションに合成できる。途中で失敗すると、前の書き込みも取り消される
- 版の確認と主キーの一意制約で、同時に書く 2 つの接続のうち一方だけが成功する
- テストは Testcontainers で自己完結し、専用のテーブルを `Resource` で作って捨てる
- 想定外の失敗は 500 にし、理由を応答に出さない
- ドメイン、HTTP、データベースの 3 経路で、同じシナリオが通る

doobie は 1.0.0-RC13 で、安定版ではありません。パッケージ名が変わったことが実際のつまずきでした。

`sbt test` は 81 本が通ります（第 8 章の 62 本に、19 本を足しました）。結合テストは Docker が要ります。

次章では、コンテキストを読み込み、コマンドを処理する流れを作ります。

## この章で書いたコード

- `apps/scala/zettai/src/main/scala/zettai/domain/errors/Errors.scala`
- `apps/scala/zettai/src/main/scala/zettai/application/EventStore.scala`
- `apps/scala/zettai/src/main/scala/zettai/adapter/postgres/PostgresStore.scala`
- `apps/scala/zettai/src/main/scala/zettai/web/ZettaiRoutes.scala`
- `apps/scala/zettai/src/test/scala/zettai/adapter/postgres/TestDatabase.scala`
- `apps/scala/zettai/src/test/scala/zettai/adapter/postgres/PostgresStoreSuite.scala`
- `apps/scala/zettai/src/test/scala/zettai/adapter/postgres/ConnectionIOSuite.scala`
- `apps/scala/zettai/src/test/scala/zettai/web/UnexpectedFailureSuite.scala`
- `apps/scala/zettai/src/test/scala/zettai/acceptance/Routes.scala`
- `apps/scala/zettai/src/test/scala/zettai/acceptance/AcceptanceDbSuite.scala`
- `apps/scala/zettai/src/test/scala/zettai/adapter/postgres/HttpOverPostgresSuite.scala`

## 参照

- [ADR-051 doobie と Testcontainers](../../../adr/ADR-051-scala-doobie-and-testcontainers.md)
- [doobie](https://typelevel.org/doobie/)
- [Testcontainers for Java](https://java.testcontainers.org/)
