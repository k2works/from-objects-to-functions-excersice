package zettai

import cats.effect.IO
import com.comcast.ip4s.*
import munit.CatsEffectSuite
import org.http4s.Status
import org.http4s.ember.client.EmberClientBuilder

class ServerSuite extends CatsEffectSuite:

  private val book = ToDoList(ListName("book"), List(ToDoItem.todo("write <chapter>")))
  private val hub = Hub.inMemory(Map(User("uberto") -> List(book)))

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
