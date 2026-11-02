package zettai

import cats.effect.IO
import com.comcast.ip4s.*
import munit.CatsEffectSuite
import org.http4s.Status
import org.http4s.ember.client.EmberClientBuilder
import zettai.ToDoListCommand.*

class HttpOverPostgresSuite extends CatsEffectSuite:

  private val uberto = User("uberto")
  private val book   = ListName("book")

  test("コマンドで PostgreSQL に書いたものを、HTTP で読める") {
    val xa = PostgresStore.transactor(TestDatabase.config)
    val resources =
      for
        table  <- cats.effect.Resource.eval(PostgresStore.freshTableName)
        store  <- cats.effect.Resource.make(PostgresStore.open(xa, table))(_ => PostgresStore.dropTable(xa, table))
        server <- Server.start(ZettaiRoutes.routes(Hub.fromStore(store)), port"0")
        client <- EmberClientBuilder.default[IO].build
      yield (store, server, client)

    resources.use { (store, server, client) =>
      val commands = CommandHub.commandHub(store)
      val base     = s"http://127.0.0.1:${server.address.getPort}"
      for
        _       <- commands.execute(CreateToDoList(uberto, book))
        _       <- commands.execute(AddToDoItem(uberto, book, ToDoItem.todo("write <chapter>")))
        found   <- client.get(s"$base/todo/uberto/book")(r => r.as[String].map(b => (r.status, b)))
        missing <- client.get(s"$base/todo/uberto/nothing")(r => IO.pure(r.status))
        stale   <- commands.executeAt(0, AddToDoItem(uberto, book, ToDoItem.todo("late")))
      yield
        assertEquals(found._1, Status.Ok)
        assert(found._2.contains("<li>write &lt;chapter&gt;</li>"))
        assertEquals(missing, Status.NotFound)
        assertEquals(stale, Left(ZettaiError.VersionConflict(0, 2)))
    }
  }
