package zettai

import cats.effect.IO
import munit.CatsEffectSuite

class AcceptanceDbSuite extends CatsEffectSuite:

  private val lists = Map(
    User("uberto") -> List(
      ToDoList(
        ListName("book"),
        List(
          ToDoItem.todo("write chapter"),
          ToDoItem.todo("publish book"),
          ToDoItem.todo("read <today> & \"FP\"")
        )
      )
    )
  )

  private def onEveryRoute(scenario: ZettaiActions => IO[Unit]): IO[Unit] =
    Routes.onEveryRoute(lists)(scenario) *> Routes.databaseActions(lists).use(scenario)

  test("データベースの経路でも、リストの項目が見える") {
    onEveryRoute(Scenarios.seeTheItemsOfAList)
  }

  test("データベースの経路でも、存在しないリストは見つからない") {
    onEveryRoute(Scenarios.aMissingListIsNotFound)
  }
