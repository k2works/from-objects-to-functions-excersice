package zettai.web

import zettai.application.Hub
import zettai.domain.{ListName, User}
import zettai.domain.commands.ToDoListCommand
import zettai.domain.errors.ZettaiError
import zettai.domain.validation.Validate

import cats.effect.IO
import cats.syntax.all.*
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
      case ZettaiError.VersionConflict(_, _)      => 409
      case ZettaiError.InvalidInput(_)            => 400

  private def failure(error: ZettaiError): IO[Response[IO]] =
    val status = Status.fromInt(statusOf(error)).getOrElse(Status.InternalServerError)
    error match
      case ZettaiError.InvalidInput(problems) => IO.pure(Response[IO](status).withEntity(Validate.explain(problems)))
      case other                              => IO.pure(Response[IO](status).withEntity(other.toString))

  /** 名前の変更。入力の誤りは、最初の 1 つで止めず、すべてまとめて返す。 */
  def renameRoutes(execute: ToDoListCommand => IO[Either[ZettaiError, Unit]]): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case req @ POST -> Root / "todo" / user / list / "rename" =>
        req.as[String].flatMap { newName =>
          Validate.validateRename(user, list, newName).toEither match
            case Left(problems) => failure(ZettaiError.InvalidInput(problems.toNonEmptyList.toList))
            case Right(command) =>
              execute(command).flatMap {
                case Right(_)    => Ok("renamed")
                case Left(error) => failure(error)
              }
        }
    }

  /** 読み取りは GET、名前変更は POST。 */
  def application(hub: Hub, execute: ToDoListCommand => IO[Either[ZettaiError, Unit]]): HttpRoutes[IO] =
    routes(hub) <+> renameRoutes(execute)

  def routes(hub: Hub): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "todo" / user / list =>
        hub
          .getList(User(user), ListName(list))
          .flatMap {
            case Right(found) => Ok(Html.render(found)).map(_.withContentType(html))
            case Left(error)  => failure(error)
          }
          // 想定外の失敗（接続切れなど）。理由には接続先や SQL が入りうるので、本文には出さない
          .handleErrorWith(_ => InternalServerError("Internal Server Error"))
    }
