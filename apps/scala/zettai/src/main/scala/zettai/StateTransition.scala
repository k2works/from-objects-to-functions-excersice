package zettai

import cats.kernel.Monoid

/** 出来事を 1 つ固定すると、「状態を受け取り、状態を返す関数」になる。 */
final case class StateTransition(run: ToDoListState => ToDoListState)

object StateTransition:

  def fromEvent(event: ToDoListEvent): StateTransition =
    StateTransition(state => Events.apply(state, event))

  /** 順番につなぐ。a の後に b を行う。 */
  def andThen(a: StateTransition, b: StateTransition): StateTransition =
    StateTransition(state => b.run(a.run(state)))

  given Monoid[StateTransition] with
    def empty: StateTransition = StateTransition(state => state)
    def combine(a: StateTransition, b: StateTransition): StateTransition = andThen(a, b)

  def fromEvents(events: List[ToDoListEvent]): StateTransition =
    Monoid[StateTransition].combineAll(events.map(fromEvent))
