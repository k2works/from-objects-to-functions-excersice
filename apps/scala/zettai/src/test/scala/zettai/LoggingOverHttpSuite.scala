package zettai

import cats.effect.{IO, Ref}
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.implicits.*

class LoggingOverHttpSuite extends CatsEffectSuite:

  private val lines: IO[(LogSink, IO[List[LogRecord]])] =
    Ref.of[IO, List[String]](Nil).map { ref =>
      (
        LogSink(line => ref.update(_ :+ line)),
        ref.get.map(_.map(line => Logs.fromJson(Json.parse(line).toOption.get).toOption.get))
      )
    }

  private val clock: IO[Long] = IO.pure(0L)

  test("保管場所を包むと、読み取りも書き込みも、データベース呼び出しとして記録される") {
    for
      pair     <- lines
      inner    <- EventStore.inMemory
      store     = Logging.observeStore(pair._1, clock, inner)
      commands  = Logging.observeCommandHub(pair._1, clock, CommandHub.commandHub(store))
      hub       = Logging.observeHub(pair._1, clock, Hub.fromStore(store))
      app       = ZettaiRoutes.application(hub, commands.execute).orNotFound
      _        <- commands.execute(ToDoListCommand.CreateToDoList(User("uberto"), ListName("book")))
      _        <- commands.execute(ToDoListCommand.AddToDoItem(User("uberto"), ListName("book"), ToDoItem.todo("secret item")))
      response <- app.run(Request[IO](Method.GET, uri"/todo/uberto/book"))
      missing  <- app.run(Request[IO](Method.GET, uri"/todo/uberto/nothing"))
      records  <- pair._2
    yield
      assertEquals(response.status, Status.Ok)
      assertEquals(missing.status, Status.NotFound)
      // 内側の保管場所の記録が先に、外側の入口の記録があとに並ぶ
      assertEquals(
        records.map(r => (r.kind, r.name, r.outcome)),
        List(
          ("store", "load", "ok"),
          ("store", "append", "ok"),
          ("command", "CreateToDoList", "ok"),
          ("store", "load", "ok"),
          ("store", "append", "ok"),
          ("command", "AddToDoItem", "ok"),
          ("store", "load", "ok"),
          ("hub", "getList", "ok"),
          ("store", "load", "ok"),
          ("hub", "getList", "ListNotFound")
        )
      )
  }
