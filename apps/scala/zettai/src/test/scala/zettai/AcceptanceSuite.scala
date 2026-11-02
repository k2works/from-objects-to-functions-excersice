package zettai

import munit.CatsEffectSuite

class AcceptanceSuite extends CatsEffectSuite:

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

  test("リストの項目が見える") {
    Routes.onEveryRoute(lists)(Scenarios.seeTheItemsOfAList)
  }

  test("存在しないリストは見つからない") {
    Routes.onEveryRoute(lists)(Scenarios.aMissingListIsNotFound)
  }

  test("リストを持たない利用者は何も見つけられない") {
    Routes.onEveryRoute(Map.empty)(Scenarios.aMissingListIsNotFound)
  }
