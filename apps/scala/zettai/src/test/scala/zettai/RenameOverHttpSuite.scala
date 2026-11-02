package zettai

import cats.effect.IO
import com.comcast.ip4s.*
import munit.CatsEffectSuite
import org.http4s.{Method, Request, Status, Uri}
import org.http4s.ember.client.EmberClientBuilder
import zettai.ToDoListCommand.*

class RenameOverHttpSuite extends CatsEffectSuite:

  private val uberto = User("uberto")
  private val book   = ListName("book")

  private def withServer(body: (String, EventStore) => IO[Unit]): IO[Unit] =
    EventStore.inMemory.flatMap { store =>
      val commands = CommandHub.commandHub(store)
      val routes   = ZettaiRoutes.application(Hub.fromStore(store), commands.execute)
      (for
        server <- Server.start(routes, port"0")
        client <- EmberClientBuilder.default[IO].build
      yield (server, client)).use { (server, _) =>
        val base = s"http://127.0.0.1:${server.address.getPort}"
        for
          _ <- commands.execute(CreateToDoList(uberto, book))
          _ <- commands.execute(AddToDoItem(uberto, book, ToDoItem.todo("write & <publish>")))
          _ <- body(base, store)
        yield ()
      }
    }

  private def post(base: String, path: String, body: String): IO[(Status, String)] =
    EmberClientBuilder.default[IO].build.use { client =>
      client.run(Request[IO](Method.POST, Uri.unsafeFromString(base + path)).withEntity(body)).use { r =>
        r.as[String].map(text => (r.status, text))
      }
    }

  private def get(base: String, path: String): IO[(Status, String)] =
    EmberClientBuilder.default[IO].build.use { client =>
      client.run(Request[IO](Method.GET, Uri.unsafeFromString(base + path))).use { r =>
        r.as[String].map(text => (r.status, text))
      }
    }

  test("実サーバーで名前を変えると、旧い名前は 404 になり、新しい名前で項目が読める") {
    withServer { (base, _) =>
      for
        renamed  <- post(base, "/todo/uberto/book/rename", "shop")
        oldName  <- get(base, "/todo/uberto/book")
        newName  <- get(base, "/todo/uberto/shop")
      yield
        assertEquals(renamed._1, Status.Ok)
        assertEquals(oldName._1, Status.NotFound)
        assertEquals(newName._1, Status.Ok)
        assert(newName._2.contains("write &amp; &lt;publish&gt;"))
    }
  }

  test("入力の誤りは 400 で、すべてまとめて返し、何も書かない") {
    withServer { (base, store) =>
      for
        before   <- store.load
        response <- post(base, "/todo/uberto/book/rename", "a/" + "b" * 40)
        after    <- store.load
      yield
        assertEquals(response._1, Status.BadRequest)
        assert(response._2.contains("新しい名前は 40 文字以内にしてください"))
        assert(response._2.contains("新しい名前に使えない文字が含まれています"))
        assertEquals(after, before)
    }
  }

  test("存在しないリストの名前変更は 404") {
    withServer { (base, _) =>
      post(base, "/todo/uberto/nothing/rename", "shop").map(r => assertEquals(r._1, Status.NotFound))
    }
  }
