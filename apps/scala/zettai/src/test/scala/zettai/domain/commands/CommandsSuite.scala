package zettai.domain.commands

import zettai.domain.{Domain, ListName, ToDoItem, ToDoStatus, User}
import zettai.domain.errors.ZettaiError
import zettai.domain.events.{Events, Replay, ToDoListEvent, ToDoListState}

import munit.FunSuite
import zettai.domain.commands.ToDoListCommand.*

class CommandsSuite extends FunSuite:

  private val uberto = User("uberto")
  private val book   = ListName("book")

  private def withItem: ToDoListState =
    Replay.replayFrom(
      Events.empty,
      List(
        ToDoListEvent.ListCreated(uberto, book),
        ToDoListEvent.ItemAdded(uberto, book, ToDoItem.todo("write chapter"))
      )
    )

  test("新しいリストを作る") {
    assertEquals(
      Commands.handle(CreateToDoList(uberto, book), Events.empty),
      Right(List(ToDoListEvent.ListCreated(uberto, book)))
    )
  }

  test("すでにあるリストの再作成は拒否する") {
    assertEquals(
      Commands.handle(CreateToDoList(uberto, book), withItem),
      Left(ZettaiError.ListAlreadyExists(uberto, book))
    )
  }

  test("あるリストに項目を追加する") {
    assertEquals(
      Commands.handle(AddToDoItem(uberto, book, ToDoItem.todo("publish")), withItem),
      Right(List(ToDoListEvent.ItemAdded(uberto, book, ToDoItem.todo("publish"))))
    )
  }

  test("無いリストへの項目の追加は拒否する") {
    assertEquals(
      Commands.handle(AddToDoItem(uberto, book, ToDoItem.todo("publish")), Events.empty),
      Left(ZettaiError.ListNotFound(uberto, book))
    )
  }

  test("あるリストの項目の状態を変える") {
    assertEquals(
      Commands.handle(ChangeItemStatus(uberto, book, "write chapter", ToDoStatus.InProgress), withItem),
      Right(List(ToDoListEvent.ItemStatusChanged(uberto, book, "write chapter", ToDoStatus.InProgress)))
    )
  }

  test("無い項目の状態変更は拒否する") {
    assertEquals(
      Commands.handle(ChangeItemStatus(uberto, book, "missing", ToDoStatus.Done), withItem),
      Left(ZettaiError.ItemNotFound(uberto, book, "missing"))
    )
  }

  test("無いリストの状態変更は拒否する") {
    assertEquals(
      Commands.handle(ChangeItemStatus(uberto, book, "write chapter", ToDoStatus.Done), Events.empty),
      Left(ZettaiError.ListNotFound(uberto, book))
    )
  }

  test("遷移表が許さない遷移は拒否する") {
    assertEquals(
      Commands.handle(ChangeItemStatus(uberto, book, "write chapter", ToDoStatus.Blocked), withItem),
      Left(ZettaiError.InvalidTransition("write chapter", ToDoStatus.Todo, ToDoStatus.Blocked))
    )
  }

  test("状態の変更は、説明が最初に一致した項目だけに適用される") {
    val twin = Replay.replayFrom(
      withItem,
      List(ToDoListEvent.ItemAdded(uberto, book, ToDoItem.todo("write chapter")))
    )
    val after = Replay.replayFrom(
      twin,
      List(ToDoListEvent.ItemStatusChanged(uberto, book, "write chapter", ToDoStatus.Done))
    )
    assertEquals(
      Events.listFor(after, uberto, book).map(_.items.map(_.status)),
      Some(List(ToDoStatus.Done, ToDoStatus.Todo))
    )
  }

  test("Done からは戻せない") {
    assertEquals(
      ToDoStatus.values.toList.filter(next => Domain.canTransition(ToDoStatus.Done, next)),
      Nil
    )
  }
