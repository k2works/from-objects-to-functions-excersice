package zettai

import cats.effect.IO

/** 読み取りの依存を、関数を持つ値として渡す。 */
final case class Hub(getList: (User, ListName) => IO[Either[ZettaiError, ToDoList]])

object Hub:

  /** イベントストアから、読むたびに状態を作り直して答える。 */
  def fromStore(store: EventStore): Hub =
    Hub((user, name) =>
      store.load.map { read =>
        Events
          .listFor(Replay.replayFrom(Events.empty, read.events), user, name)
          .toRight(ZettaiError.ListNotFound(user, name))
      }
    )

  def inMemory(lists: Map[User, List[ToDoList]]): Hub =
    Hub((user, name) =>
      IO.pure(Domain.findToDoList(lists, user, name).toRight(ZettaiError.ListNotFound(user, name)))
    )
