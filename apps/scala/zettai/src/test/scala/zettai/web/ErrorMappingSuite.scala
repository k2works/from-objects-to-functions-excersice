package zettai.web

import zettai.domain.{ListName, ToDoStatus, User}
import zettai.domain.errors.ZettaiError

import munit.FunSuite

class ErrorMappingSuite extends FunSuite:

  private val uberto = User("uberto")
  private val book   = ListName("book")

  test("見つからない失敗は 404") {
    assertEquals(ZettaiRoutes.statusOf(ZettaiError.ListNotFound(uberto, book)), 404)
    assertEquals(ZettaiRoutes.statusOf(ZettaiError.ItemNotFound(uberto, book, "x")), 404)
  }

  test("版の競合は 409") {
    assertEquals(ZettaiRoutes.statusOf(ZettaiError.VersionConflict(0, 1)), 409)
  }

  test("ドメインが拒否した要求は 400") {
    assertEquals(ZettaiRoutes.statusOf(ZettaiError.ListAlreadyExists(uberto, book)), 400)
    assertEquals(
      ZettaiRoutes.statusOf(ZettaiError.InvalidTransition("x", ToDoStatus.Todo, ToDoStatus.Blocked)),
      400
    )
  }
