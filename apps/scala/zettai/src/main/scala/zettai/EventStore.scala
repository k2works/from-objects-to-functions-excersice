package zettai

import cats.effect.{IO, Ref}

/** イベントの置き場所。使う側は、どこに保存されるかを知らない。 */
final case class EventStore(
    load: IO[List[ToDoListEvent]],
    append: List[ToDoListEvent] => IO[Unit]
)

object EventStore:

  /** 可変の Ref は IO の中で作る。作ること自体が効果なので、IO[EventStore] を返す。 */
  def inMemory: IO[EventStore] =
    Ref.of[IO, List[ToDoListEvent]](Nil).map { history =>
      EventStore(history.get, events => history.update(_ ::: events))
    }
