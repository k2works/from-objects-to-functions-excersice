package zettai

import cats.effect.{IO, Ref}

/** 読み出した時点の版（これまでに書かれたイベントの数）と、イベントの列。 */
final case class Versioned(version: Int, events: List[ToDoListEvent])

/** イベントの置き場所。使う側は、どこに保存されるかを知らない。 */
final case class EventStore(
    load: IO[Versioned],
    /** 読んだ版を添えて書き足す。その間に他の書き手が書いていたら、何も書かずに VersionConflict を返す。 */
    append: (Int, List[ToDoListEvent]) => IO[Either[ZettaiError, Unit]]
)

object EventStore:

  /** 可変の Ref は IO の中で作る。作ること自体が効果なので、IO[EventStore] を返す。 */
  def inMemory: IO[EventStore] =
    Ref.of[IO, Versioned](Versioned(0, Nil)).map { history =>
      EventStore(
        history.get,
        (expected, events) =>
          history.modify { current =>
            if current.version != expected then (current, Left(ZettaiError.VersionConflict(expected, current.version)))
            else (Versioned(current.version + events.size, current.events ::: events), Right(()))
          }
      )
    }
