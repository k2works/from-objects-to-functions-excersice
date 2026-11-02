package zettai.domain.queries

import zettai.Gen
import zettai.domain.{ListName, ToDoItem, ToDoStatus, User}
import zettai.domain.events.{Events, Replay, ToDoListEvent}

import munit.FunSuite

class ProjectionSuite extends FunSuite:

  private val uberto = User("uberto")
  private val alice  = User("alice")
  private val book   = ListName("book")
  private val food   = ListName("food")

  private val events = List(
    ToDoListEvent.ListCreated(uberto, book),
    ToDoListEvent.ItemAdded(uberto, book, ToDoItem.todo("write")),
    ToDoListEvent.ItemAdded(uberto, book, ToDoItem.todo("publish")),
    ToDoListEvent.ItemStatusChanged(uberto, book, "write", ToDoStatus.Done),
    ToDoListEvent.ListCreated(uberto, food),
    ToDoListEvent.ListCreated(alice, book)
  )

  test("リストごとに、項目の数と完了の数を数える") {
    assertEquals(
      Projection.projectFrom(uberto, events).sortBy(_.name.name),
      List(ToDoListRow(book, 2, 1), ToDoListRow(food, 0, 0))
    )
  }

  test("他の利用者のイベントは含めない") {
    assertEquals(Projection.projectFrom(alice, events), List(ToDoListRow(book, 0, 0)))
  }

  test("イベントが無ければ行も無い") {
    assertEquals(Projection.projectFrom(uberto, Nil), Nil)
  }

  test("同じ説明の項目があっても、状態を変えるのは最初の 1 件だけ") {
    val twin = List(
      ToDoListEvent.ListCreated(uberto, book),
      ToDoListEvent.ItemAdded(uberto, book, ToDoItem.todo("a")),
      ToDoListEvent.ItemAdded(uberto, book, ToDoItem.todo("a")),
      ToDoListEvent.ItemStatusChanged(uberto, book, "a", ToDoStatus.Done)
    )
    assertEquals(Projection.projectFrom(uberto, twin), List(ToDoListRow(book, 2, 1)))
  }

  private def rowsFromState(user: User, events: List[ToDoListEvent]): List[ToDoListRow] =
    val state = Replay.replayFrom(Events.empty, events)
    Events.listFor(state, user, book).toList.map { list =>
      ToDoListRow(list.name, list.items.size, list.items.count(_.status == ToDoStatus.Done))
    }

  test("射影は、イベントを再生した状態から数え直した行と一致する（全パターン）") {
    for events <- Gen.projectionListsUpTo(4); user <- List(uberto, alice) do
      assertEquals(Projection.projectFrom(user, events), rowsFromState(user, events))
  }
