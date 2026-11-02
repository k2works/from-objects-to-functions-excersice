package zettai.steps

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.cucumber.scala.{JA, ScalaDsl}
import zettai.*

/**
 * Cucumber の step definitions。ステップは同期の関数なので、IO は unsafeRunSync で最後まで実行する。
 * 経路（ドメイン / HTTP）は、同じ ZettaiActions で差し替える。
 */
class ListSteps extends ScalaDsl with JA:

  private var lists: Map[User, List[ToDoList]] = Map.empty
  private var found: Map[String, Option[List[ToDoItem]]] = Map.empty

  前提("""利用者 {string} のリスト {string} に項目 {string} がある""") { (user: String, list: String, item: String) =>
    lists = Map(User(user) -> List(ToDoList(ListName(list), List(ToDoItem.todo(item)))))
  }

  もし("""利用者 {string} がリスト {string} を見る""") { (user: String, list: String) =>
    found = List(Routes.domainActions(lists), Routes.httpActions(lists)).map { route =>
      route.use(actions => actions.getToDoList(User(user), ListName(list)).map(actions.name -> _)).unsafeRunSync()
    }.toMap
  }

  ならば("""項目 {string} が見える""") { (item: String) =>
    assert(found.nonEmpty)
    for (route, items) <- found do
      assert(items.contains(List(ToDoItem.todo(item))), s"経路 $route で見えなかった: $items")
  }

  ならば("""リストは見つからない""") { () =>
    assert(found.nonEmpty)
    for (route, items) <- found do assert(items.isEmpty, s"経路 $route で見つかってしまった: $items")
  }
