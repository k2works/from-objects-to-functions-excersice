package zettai.web

import zettai.application.Hub

import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.implicits.*

class UnexpectedFailureSuite extends CatsEffectSuite:

  private val secret = "jdbc:postgresql://db.internal:5432/zettai password=hunter2"

  private val brokenHub = Hub((_, _) => IO.raiseError(new RuntimeException(secret)))
  private val app      = ZettaiRoutes.routes(brokenHub).orNotFound

  test("想定外の失敗は 500 になり、本文に失敗の理由を出さない") {
    for
      response <- app.run(Request[IO](Method.GET, uri"/todo/uberto/book"))
      body     <- response.as[String]
    yield
      assertEquals(response.status, Status.InternalServerError)
      assertEquals(body, "Internal Server Error")
      assert(!body.contains("hunter2"))
  }
