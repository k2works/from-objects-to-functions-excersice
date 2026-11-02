package zettai.adapter.logging

import zettai.application.{CommandHub, EventStore, Hub}
import zettai.domain.{ListName, ToDoItem, User}
import zettai.domain.commands.ToDoListCommand
import zettai.domain.errors.ZettaiError
import zettai.json.Json
import zettai.logging.{LogRecord, Logs}

import cats.effect.{IO, Ref}
import munit.CatsEffectSuite
import zettai.domain.commands.ToDoListCommand.*

class LoggingSuite extends CatsEffectSuite:

  private val uberto = User("uberto")
  private val book   = ListName("book")

  /** 出力先をテストが握る。書かれた行を集める。 */
  private def collecting: IO[(LogSink, IO[List[String]])] =
    Ref.of[IO, List[String]](Nil).map(lines => (LogSink(line => lines.update(_ :+ line)), lines.get))

  /** 1 回呼ぶたびに 100 ミリ秒進む時計。 */
  private def clock: IO[IO[Long]] = Ref.of[IO, Long](0L).map(t => t.getAndUpdate(_ + 100))

  private def parse(line: String): LogRecord =
    Logs.fromJson(Json.parse(line).toOption.get).toOption.get

  test("コマンドの入口を包むと、結果にかかわらず 1 行が書かれる") {
    for
      pair     <- collecting
      now      <- clock
      store    <- EventStore.inMemory
      hub       = Logging.observeCommandHub(pair._1, now, CommandHub.commandHub(store))
      ok       <- hub.execute(CreateToDoList(uberto, book))
      rejected <- hub.execute(AddToDoItem(uberto, ListName("nothing"), ToDoItem.todo("secret item")))
      written  <- pair._2
    yield
      assertEquals(ok, Right(()))
      assertEquals(rejected, Left(ZettaiError.ListNotFound(uberto, ListName("nothing"))))
      assertEquals(
        written.map(parse),
        List(
          LogRecord("command", "CreateToDoList", "ok", Some("uberto"), Some("book"), 100),
          LogRecord("command", "AddToDoItem", "ListNotFound", Some("uberto"), Some("nothing"), 100)
        )
      )
      assert(written.forall(!_.contains("secret item")))
  }

  test("想定外の失敗は、種類だけを書き、理由は書かずに、そのまま投げ直す") {
    val secret = "jdbc:postgresql://db.internal/zettai password=hunter2"
    for
      pair    <- collecting
      now     <- clock
      broken   = EventStore(IO.raiseError(new RuntimeException(secret)), (_, _) => IO.pure(Right(())))
      store    = Logging.observeStore(pair._1, now, broken)
      outcome <- store.load.attempt
      written <- pair._2
    yield
      assertEquals(outcome.left.map(_.getMessage), Left(secret))
      assertEquals(written.map(parse), List(LogRecord("store", "load", "UnexpectedFailure", None, None, 100)))
      assert(written.forall(!_.contains("hunter2")))
  }

  test("ログの出力先が壊れても、処理は止まらない") {
    for
      now   <- clock
      store <- EventStore.inMemory
      sink   = LogSink(_ => IO.raiseError(new RuntimeException("disk full")))
      hub    = Logging.observeCommandHub(sink, now, CommandHub.commandHub(store))
      done  <- hub.execute(CreateToDoList(uberto, book))
      read  <- store.load
    yield
      assertEquals(done, Right(()))
      assertEquals(read.version, 1)
  }

  test("読み取りの入口を包むと、リストの名前を載せて 1 行が書かれる") {
    for
      pair    <- collecting
      now     <- clock
      hub      = Logging.observeHub(pair._1, now, Hub.inMemory(Map.empty))
      found   <- hub.getList(uberto, book)
      written <- pair._2
    yield
      assertEquals(found.isLeft, true)
      assertEquals(written.map(parse), List(LogRecord("hub", "getList", "ListNotFound", Some("uberto"), Some("book"), 100)))
  }
