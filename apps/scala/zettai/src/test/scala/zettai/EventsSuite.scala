package zettai

import munit.FunSuite

class EventsSuite extends FunSuite:

  private val uberto = User("uberto")
  private val book   = ListName("book")

  test("イベントを適用しても、前の状態は変わらない") {
    val empty   = Events.empty
    val created = Events.apply(empty, ToDoListEvent.ListCreated(uberto, book))
    val added   = Events.apply(created, ToDoListEvent.ItemAdded(uberto, book, ToDoItem.todo("write chapter")))
    assertEquals(Events.listFor(empty, uberto, book), None)
    assertEquals(Events.listFor(created, uberto, book), Some(ToDoList(book, Nil)))
    assertEquals(
      Events.listFor(added, uberto, book),
      Some(ToDoList(book, List(ToDoItem.todo("write chapter"))))
    )
  }

  test("作られていないリストへの追加は無視される") {
    val state = Events.apply(Events.empty, ToDoListEvent.ItemAdded(uberto, book, ToDoItem.todo("write chapter")))
    assertEquals(Events.listFor(state, uberto, book), None)
  }
