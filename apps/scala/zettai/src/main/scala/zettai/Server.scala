package zettai

import cats.effect.{IO, Resource}
import com.comcast.ip4s.*
import org.http4s.HttpRoutes
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server as Http4sServer

object Server:

  /** サーバーの起動と停止を Resource に閉じ込める。use を抜けると必ず止まる。 */
  def start(routes: HttpRoutes[IO], port: Port): Resource[IO, Http4sServer] =
    EmberServerBuilder
      .default[IO]
      .withHost(host"127.0.0.1")
      .withPort(port)
      .withHttpApp(routes.orNotFound)
      .build
