package zettai.domain.events

import scala.annotation.tailrec

object Replay:

  /** 学習用。標準関数を使わず、再帰の形をそのまま見せる。 */
  @tailrec
  def replayByRecursion(initial: ToDoListState, events: List[ToDoListEvent]): ToDoListState =
    events match
      case Nil     => initial
      case e :: es => replayByRecursion(Events.apply(initial, e), es)

  /** イベント列を、左から順に状態へ畳み込む。 */
  def replayFrom(initial: ToDoListState, events: List[ToDoListEvent]): ToDoListState =
    events.foldLeft(initial)(Events.apply)
