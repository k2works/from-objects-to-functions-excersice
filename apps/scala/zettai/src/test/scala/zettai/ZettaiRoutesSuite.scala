package zettai

import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.implicits.*

class ZettaiRoutesSuite extends CatsEffectSuite:

  private val book = ToDoList(ListName("book"), List(ToDoItem.todo("write chapter"), ToDoItem.todo("publish book")))
  private val hub = Hub.inMemory(Map(User("uberto") -> List(book)))
  private val app = ZettaiRoutes.routes(hub).orNotFound

  private def get(path: String): IO[Response[IO]] =
    app.run(Request[IO](Method.GET, Uri.unsafeFromString(path)))

  test("リストを HTML で返す") {
    for
      response <- get("/todo/uberto/book")
      body     <- response.as[String]
    yield
      assertEquals(response.status, Status.Ok)
      assert(body.contains("<li>write chapter</li>"))
  }

  test("存在しないリストは 404 で、失敗の理由を返す") {
    for
      response <- get("/todo/uberto/missing")
      body     <- response.as[String]
    yield
      assertEquals(response.status, Status.NotFound)
      assert(body.contains("ListNotFound"))
  }

  test("知らないパスは 404") {
    get("/other").map(r => assertEquals(r.status, Status.NotFound))
  }

  test("GET 以外は受け付けない") {
    app.run(Request[IO](Method.POST, uri"/todo/uberto/book")).map(r => assertEquals(r.status, Status.NotFound))
  }
