package zettai.application

import zettai.domain.{ListName, ToDoItem, ToDoList, ToDoStatus, User}

import cats.Id
import cats.effect.IO
import munit.CatsEffectSuite

class ViewSuite extends CatsEffectSuite:

  private val book = ToDoList(ListName("book"), List(ToDoItem.todo("write chapter")))
  private val lists = Map(User("uberto") -> List(book))
  private val hub = Hub.inMemory(lists)

  private val cases = List(
    (User("uberto"), ListName("book")),
    (User("uberto"), ListName("missing")),
    (User("nobody"), ListName("book"))
  )

  test("引数で渡す版は、リストを HTML にする") {
    View.byHub(hub, User("uberto"), ListName("book")).map { html =>
      assertEquals(html.map(_.contains("<li>write chapter</li>")), Some(true))
    }
  }

  test("Kleisli で渡す版は、引数で渡す版と同じ結果を返す") {
    cases.foldLeft(IO.unit) { case (done, (user, name)) =>
      done *> View.byHub(hub, user, name).flatMap { expected =>
        View.byReader(user, name).run(hub).map(actual => assertEquals(actual, expected))
      }
    }
  }

  test("タグレス・ファイナル版は、IO でも Id でも同じ結果を返す") {
    cases.foldLeft(IO.unit) { case (done, (user, name)) =>
      val pure: Option[String] = View.byTagless[Id](ToDoLists.inMemory[Id](lists), user, name)
      done *> View.byTagless[IO](ToDoLists.inMemory[IO](lists), user, name).map(actual => assertEquals(actual, pure))
    }
  }

  test("新しい項目は Todo から始まる") {
    assertEquals(ToDoItem.todo("write chapter"), ToDoItem("write chapter", ToDoStatus.Todo))
  }

  test("状態はすべて列挙できる") {
    assertEquals(
      ToDoStatus.values.toList,
      List(ToDoStatus.Todo, ToDoStatus.InProgress, ToDoStatus.Done, ToDoStatus.Blocked)
    )
  }
