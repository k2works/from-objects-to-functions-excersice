package zettai.domain.commands

import zettai.domain.{ListName, ToDoItem, ToDoList, ToDoStatus, User}
import zettai.domain.errors.ZettaiError
import zettai.domain.events.{Events, Replay, ToDoListEvent, ToDoListState}
import zettai.domain.queries.{Projection, ToDoListRow}

import munit.FunSuite
import zettai.domain.commands.ToDoListCommand.*

class RenameSuite extends FunSuite:

  private val uberto = User("uberto")
  private val book   = ListName("book")
  private val shop   = ListName("shop")

  private val state: ToDoListState =
    Replay.replayFrom(
      Events.empty,
      List(
        ToDoListEvent.ListCreated(uberto, book),
        ToDoListEvent.ItemAdded(uberto, book, ToDoItem.todo("write")),
        ToDoListEvent.ItemAdded(uberto, book, ToDoItem.todo("publish")),
        ToDoListEvent.ItemStatusChanged(uberto, book, "write", ToDoStatus.Done)
      )
    )

  test("名前変更は、旧い名前の消去と、新しい名前での作成と、項目の状態ごとの引き継ぎになる") {
    assertEquals(
      Commands.handle(RenameToDoList(uberto, book, shop), state),
      Right(
        List(
          ToDoListEvent.ListRenamed(uberto, book, shop),
          ToDoListEvent.ListCreated(uberto, shop),
          ToDoListEvent.ItemAdded(uberto, shop, ToDoItem("write", ToDoStatus.Done)),
          ToDoListEvent.ItemAdded(uberto, shop, ToDoItem.todo("publish"))
        )
      )
    )
  }

  test("適用すると、旧い名前のリストは無くなり、新しい名前のリストが項目を状態ごと引き継ぐ") {
    val renamed = Handled.apply(state, RenameToDoList(uberto, book, shop))
    assertEquals(Events.listFor(renamed, uberto, book), None)
    assertEquals(
      Events.listFor(renamed, uberto, shop),
      Some(ToDoList(shop, List(ToDoItem("write", ToDoStatus.Done), ToDoItem.todo("publish"))))
    )
  }

  test("無いリストの名前変更は拒否する") {
    assertEquals(
      Commands.handle(RenameToDoList(uberto, shop, book), state),
      Left(ZettaiError.ListNotFound(uberto, shop))
    )
  }

  test("すでにある名前への変更は拒否する") {
    val both = Replay.replayFrom(state, List(ToDoListEvent.ListCreated(uberto, shop)))
    assertEquals(
      Commands.handle(RenameToDoList(uberto, book, shop), both),
      Left(ZettaiError.ListAlreadyExists(uberto, shop))
    )
  }

  test("射影も、名前変更後のリストを数える") {
    val events = List(
      ToDoListEvent.ListCreated(uberto, book),
      ToDoListEvent.ItemAdded(uberto, book, ToDoItem.todo("write")),
      ToDoListEvent.ListRenamed(uberto, book, shop),
      ToDoListEvent.ListCreated(uberto, shop),
      ToDoListEvent.ItemAdded(uberto, shop, ToDoItem.todo("write"))
    )
    assertEquals(Projection.projectFrom(uberto, events), List(ToDoListRow(shop, 1, 0)))
  }

  /** 状態にコマンドの結果を適用する。拒否されたら状態を変えない。 */
  private object Handled:
    def apply(state: ToDoListState, command: ToDoListCommand): ToDoListState =
      Commands.handle(command, state).fold(_ => state, Replay.replayFrom(state, _))
