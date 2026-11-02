package zettai.app

import zettai.application.Hub
import zettai.domain.{ListName, ToDoItem, ToDoList, User}
import zettai.web.{Server, ZettaiRoutes}

import cats.effect.{ExitCode, IO, IOApp}

/** IO を実行する境界。ここから外は、IO の値を組み立てるだけにする。 */
object Main extends IOApp:

  private val sample = ToDoList(ListName("book"), List(ToDoItem.todo("write chapter"), ToDoItem.todo("publish book")))

  def run(args: List[String]): IO[ExitCode] =
    AppConfig.portFrom(sys.env.get("ZETTAI_PORT")) match
      case Left(problem) => IO.println(problem).as(ExitCode.Error)
      case Right(port) =>
        val hub = Hub.inMemory(Map(User("uberto") -> List(sample)))
        Server.start(ZettaiRoutes.routes(hub), port).useForever.as(ExitCode.Success)
