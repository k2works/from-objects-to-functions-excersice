package zettai

import cats.effect.IO
import munit.Assertions.*

object Scenarios:

  /** 失敗メッセージに経路の名前が出るよう、経路名と結果を組にして比べる。 */
  private def check(actions: ZettaiActions, expected: Option[List[ToDoItem]], actual: Option[List[ToDoItem]]): Unit =
    assertEquals((actions.name, actual), (actions.name, expected))

  def seeTheItemsOfAList(actions: ZettaiActions): IO[Unit] =
    actions.getToDoList(User("uberto"), ListName("book")).map { found =>
      check(
        actions,
        Some(
          List(
            ToDoItem.todo("write chapter"),
            ToDoItem.todo("publish book"),
            ToDoItem.todo("read <today> & \"FP\"")
          )
        ),
        found
      )
    }

  def aMissingListIsNotFound(actions: ZettaiActions): IO[Unit] =
    actions.getToDoList(User("uberto"), ListName("missing")).map(found => check(actions, None, found))
