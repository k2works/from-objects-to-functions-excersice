package zettai.adapter.postgres

import zettai.application.{CommandHub, EventStore, QueryHub}
import zettai.domain.{ListName, ToDoItem, ToDoStatus, User}
import zettai.domain.commands.ToDoListCommand
import zettai.domain.queries.ToDoListRow

import cats.effect.IO
import munit.CatsEffectSuite
import zettai.domain.commands.ToDoListCommand.*

class QueryOnDatabaseSuite extends CatsEffectSuite:

  private val uberto = User("uberto")
  private val book   = ListName("book")

  test("データベースのイベントから作った行は、インメモリで作った行と同じ") {
    val xa = PostgresStore.transactor(TestDatabase.config)
    val script = List(
      CreateToDoList(uberto, book),
      AddToDoItem(uberto, book, ToDoItem.todo("write")),
      AddToDoItem(uberto, book, ToDoItem.todo("publish")),
      ChangeItemStatus(uberto, book, "write", ToDoStatus.Done)
    )
    for
      table    <- PostgresStore.freshTableName
      db       <- PostgresStore.open(xa, table)
      memory   <- EventStore.inMemory
      _        <- script.foldLeft(IO.unit)((done, c) => done *> CommandHub.commandHub(db).execute(c).void)
      _        <- script.foldLeft(IO.unit)((done, c) => done *> CommandHub.commandHub(memory).execute(c).void)
      dbRows   <- QueryHub.queryHub(db).listRows(uberto)
      memRows  <- QueryHub.queryHub(memory).listRows(uberto)
      _        <- PostgresStore.dropTable(xa, table)
    yield
      assertEquals(dbRows, List(ToDoListRow(book, 2, 1)))
      assertEquals(dbRows, memRows)
  }
