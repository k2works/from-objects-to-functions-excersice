package zettai.domain

import munit.FunSuite

class DomainSuite extends FunSuite:

  private val book = ToDoList(ListName("book"), List(ToDoItem.todo("write chapter"), ToDoItem.todo("publish book")))
  private val lists = Map(User("uberto") -> List(book))

  test("利用者のリストを名前で見つける") {
    assertEquals(Domain.findToDoList(lists, User("uberto"), ListName("book")), Some(book))
  }

  test("リストが無ければ None を返す") {
    assertEquals(Domain.findToDoList(lists, User("uberto"), ListName("missing")), None)
  }

  test("利用者が無ければ None を返す") {
    assertEquals(Domain.findToDoList(lists, User("nobody"), ListName("book")), None)
  }
