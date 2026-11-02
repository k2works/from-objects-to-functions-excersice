package zettai

import munit.CatsEffectSuite

class HubSuite extends CatsEffectSuite:

  private val book = ToDoList(ListName("book"), List(ToDoItem.todo("write chapter")))
  private val hub  = Hub.inMemory(Map(User("uberto") -> List(book)))

  test("見つかったリストは Right で返る") {
    hub.getList(User("uberto"), ListName("book")).map(found => assertEquals(found, Right(book)))
  }

  test("見つからないことは、理由つきの Left で返る") {
    hub.getList(User("uberto"), ListName("missing")).map { found =>
      assertEquals(found, Left(ZettaiError.ListNotFound(User("uberto"), ListName("missing"))))
    }
  }
