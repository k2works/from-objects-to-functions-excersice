package zettai.application

import zettai.domain.{ListName, ToDoItem, ToDoStatus, User}
import zettai.domain.commands.ToDoListCommand
import zettai.domain.queries.ToDoListRow

import munit.CatsEffectSuite
import zettai.domain.commands.ToDoListCommand.*

class QueryHubSuite extends CatsEffectSuite:

  private val uberto = User("uberto")
  private val book   = ListName("book")

  test("書く側で起きた変更が、読む側の行に現れる") {
    for
      store    <- EventStore.inMemory
      commands  = CommandHub.commandHub(store)
      queries   = QueryHub.queryHub(store)
      _        <- commands.execute(CreateToDoList(uberto, book))
      _        <- commands.execute(AddToDoItem(uberto, book, ToDoItem.todo("write")))
      _        <- commands.execute(ChangeItemStatus(uberto, book, "write", ToDoStatus.Done))
      rows     <- queries.listRows(uberto)
    yield assertEquals(rows, List(ToDoListRow(book, 1, 1)))
  }
