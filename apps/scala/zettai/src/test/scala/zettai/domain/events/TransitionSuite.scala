package zettai.domain.events

import zettai.Gen
import zettai.domain.{ListName, ToDoItem, User}

import cats.{Endo, MonoidK}
import cats.kernel.Monoid
import cats.syntax.all.*
import munit.FunSuite

class TransitionSuite extends FunSuite:

  private val uberto = User("uberto")
  private val book   = ListName("book")

  private val seeds: List[ToDoListState] =
    List(Events.empty, Replay.replayFrom(Events.empty, List(ToDoListEvent.ListCreated(uberto, book))))

  private val transitions: List[StateTransition] =
    Gen.universe.take(6).map(StateTransition.fromEvent) :+ Monoid[StateTransition].empty

  /** 関数は == で比べられない。用意した初期状態のすべてで同じ結果を返すことを、等しいことの定義にする。 */
  private def same(a: StateTransition, b: StateTransition): Boolean =
    seeds.forall(seed => a.run(seed) == b.run(seed))

  test("結合律") {
    for a <- transitions; b <- transitions; c <- transitions do
      assert(same((a |+| b) |+| c, a |+| (b |+| c)))
  }

  test("単位元") {
    for t <- transitions do
      assert(same(Monoid[StateTransition].empty |+| t, t))
      assert(same(t |+| Monoid[StateTransition].empty, t))
  }

  test("畳み込んだ変換は、出来事を順に適用した結果と等しい") {
    for events <- Gen.eventListsUpTo(3); seed <- seeds do
      assertEquals(StateTransition.fromEvents(events).run(seed), Replay.replayFrom(seed, events))
  }

  test("交換律は成り立たない。順番が結果を変える") {
    val created = StateTransition.fromEvent(ToDoListEvent.ListCreated(uberto, book))
    val added   = StateTransition.fromEvent(ToDoListEvent.ItemAdded(uberto, book, ToDoItem.todo("a")))
    assert(!same(created |+| added, added |+| created))
  }

  test("Cats の Endo のモノイドは、合成の向きが逆。reverse すると自作の畳み込みと一致する") {
    val endo = MonoidK[Endo].algebra[ToDoListState]
    for events <- Gen.eventListsUpTo(3); seed <- seeds do
      val viaCats = endo.combineAll(events.map(e => (s: ToDoListState) => Events.apply(s, e)).reverse)
      assertEquals(viaCats(seed), StateTransition.fromEvents(events).run(seed))
  }
