package zettai

import cats.effect.{IO, Resource}
import com.comcast.ip4s.*
import org.http4s.Status
import org.http4s.ember.client.EmberClientBuilder

object Routes:

  /** ドメインだけを通る経路。HTTP を知らない。 */
  def domainActions(lists: Map[User, List[ToDoList]]): Resource[IO, ZettaiActions] =
    Resource.pure(
      ZettaiActions(
        "domain",
        (user, name) => IO.pure(Domain.findToDoList(lists, user, name).map(_.items))
      )
    )

  /** 実際の HTTP サーバーを起動し、HTML を読み戻す経路。 */
  def httpActions(lists: Map[User, List[ToDoList]]): Resource[IO, ZettaiActions] =
    for
      server <- Server.start(ZettaiRoutes.routes(Hub.inMemory(lists)), port"0")
      client <- EmberClientBuilder.default[IO].build
    yield
      val base = s"http://127.0.0.1:${server.address.getPort}"
      ZettaiActions(
        "http",
        (user, name) =>
          client.get(s"$base/todo/${user.name}/${name.name}") { response =>
            if response.status == Status.Ok then response.as[String].map(body => Some(HtmlReader.items(body)))
            else IO.pure(None)
          }
      )

  /** 実際の PostgreSQL にイベントを書き、読み戻して状態を作る経路。 */
  def databaseActions(lists: Map[User, List[ToDoList]]): Resource[IO, ZettaiActions] =
    val xa = PostgresStore.transactor(TestDatabase.config)
    for
      table <- Resource.eval(PostgresStore.freshTableName)
      store <- Resource.make(PostgresStore.open(xa, table))(_ => PostgresStore.dropTable(xa, table))
      _     <- Resource.eval(store.append(0, eventsOf(lists)))
    yield
      val hub = Hub.fromStore(store)
      ZettaiActions(
        "database",
        (user, name) => hub.getList(user, name).map(_.toOption.map(_.items))
      )

  /** リストの集まりを、それを作ったイベントの列に戻す。 */
  private def eventsOf(lists: Map[User, List[ToDoList]]): List[ToDoListEvent] =
    lists.toList.flatMap { (user, userLists) =>
      userLists.flatMap { list =>
        ToDoListEvent.ListCreated(user, list.name) :: list.items.map(ToDoListEvent.ItemAdded(user, list.name, _))
      }
    }

  /** 同じシナリオを、すべての経路で実行する。 */
  def onEveryRoute(lists: Map[User, List[ToDoList]])(scenario: ZettaiActions => IO[Unit]): IO[Unit] =
    List(domainActions(lists), httpActions(lists)).foldLeft(IO.unit) { (done, route) =>
      done *> route.use(scenario)
    }
