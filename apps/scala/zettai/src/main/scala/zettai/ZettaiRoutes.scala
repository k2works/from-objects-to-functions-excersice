package zettai

import cats.effect.IO
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.headers.`Content-Type`

object ZettaiRoutes:

  private val html = `Content-Type`(MediaType.text.html, Charset.`UTF-8`)

  def routes(hub: Hub): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "todo" / user / list =>
        hub.getList(User(user), ListName(list)).flatMap {
          case Some(found) => Ok(Html.render(found)).map(_.withContentType(html))
          case None        => NotFound()
        }
    }
