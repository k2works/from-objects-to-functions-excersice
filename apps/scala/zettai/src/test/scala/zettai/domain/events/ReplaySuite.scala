package zettai.domain.events

import zettai.Gen
import zettai.domain.{ListName, User}

import munit.FunSuite

class ReplaySuite extends FunSuite:

  private val seeds: List[ToDoListState] =
    List(Events.empty, Replay.replayFrom(Events.empty, List(ToDoListEvent.ListCreated(User("uberto"), ListName("book")))))

  test("再帰版と畳み込み版は同じ状態を返す") {
    for seed <- seeds; events <- Gen.eventListsUpTo(3) do
      assertEquals(Replay.replayByRecursion(seed, events), Replay.replayFrom(seed, events))
  }

  test("列を 2 つに分けて畳み込んでも、一度に畳み込んだ状態と同じ") {
    for a <- Gen.eventListsUpTo(2); b <- Gen.eventListsUpTo(2) do
      assertEquals(
        Replay.replayFrom(Replay.replayFrom(Events.empty, a), b),
        Replay.replayFrom(Events.empty, a ::: b)
      )
  }
