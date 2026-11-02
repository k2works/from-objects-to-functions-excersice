package zettai

import munit.FunSuite
import zettai.ToDoListCommand.*

class CommandInvariantsSuite extends FunSuite:

  private val uberto = User("uberto")
  private val book   = ListName("book")
  private val food   = ListName("food")

  private val commands: List[ToDoListCommand] =
    List(
      CreateToDoList(uberto, book),
      CreateToDoList(uberto, food),
      AddToDoItem(uberto, book, ToDoItem.todo("a")),
      AddToDoItem(uberto, food, ToDoItem.todo("a"))
    ) ::: ToDoStatus.values.toList.map(status => ChangeItemStatus(uberto, book, "a", status))

  /** コマンドの列を順に実行し、受け入れられたコマンドごとに、その前後の状態を集める。 */
  private def accepted(sequence: List[ToDoListCommand]): List[(ToDoListState, ToDoListState)] =
    sequence
      .foldLeft((Events.empty, List.empty[(ToDoListState, ToDoListState)])) { case ((state, seen), command) =>
        Commands.handle(command, state) match
          case Right(events) =>
            val next = Replay.replayFrom(state, events)
            (next, (state, next) :: seen)
          case Left(_) => (state, seen)
      }
      ._2

  test("受け入れられたコマンドは、必ず状態を変える") {
    for length <- 0 to 3; sequence <- Gen.ofLength(length, commands) do
      for (before, after) <- accepted(sequence) do assertNotEquals(after, before)
  }
