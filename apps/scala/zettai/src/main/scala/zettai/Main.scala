package zettai

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.*

/** IO を実行する境界。ここから外は、IO の値を組み立てるだけにする。 */
object Main extends IOApp:

  private val sample = ToDoList(ListName("book"), List(ToDoItem.todo("write chapter"), ToDoItem.todo("publish book")))

  def run(args: List[String]): IO[ExitCode] =
    val hub = Hub.inMemory(Map(User("uberto") -> List(sample)))
    Server.start(ZettaiRoutes.routes(hub), port"8080").useForever.as(ExitCode.Success)
