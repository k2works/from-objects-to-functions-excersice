package zettai

import cats.effect.IO
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.headers.`Content-Type`

object ZettaiRoutes:

  private val html = `Content-Type`(MediaType.text.html, Charset.`UTF-8`)

  /** 失敗の種類を、HTTP のステータスに写す。全ケースを列挙するので、足し忘れはコンパイルで見つかる。 */
  def statusOf(error: ZettaiError): Int =
    error match
      case ZettaiError.ListNotFound(_, _)         => 404
      case ZettaiError.ItemNotFound(_, _, _)      => 404
      case ZettaiError.ListAlreadyExists(_, _)    => 400
      case ZettaiError.InvalidTransition(_, _, _) => 400

  private def failure(error: ZettaiError): IO[Response[IO]] =
    IO.pure(Response[IO](Status.fromInt(statusOf(error)).getOrElse(Status.InternalServerError)).withEntity(error.toString))

  def routes(hub: Hub): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "todo" / user / list =>
        hub.getList(User(user), ListName(list)).flatMap {
          case Right(found) => Ok(Html.render(found)).map(_.withContentType(html))
          case Left(error)  => failure(error)
        }
    }
