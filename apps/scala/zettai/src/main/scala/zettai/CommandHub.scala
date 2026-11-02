package zettai

import cats.effect.IO

/** コマンドの入口。実行すると、拒否の理由か、何も返さない成功を返す。 */
final case class CommandHub(
    execute: ToDoListCommand => IO[Either[ZettaiError, Unit]],
    current: IO[ToDoListState]
)

object CommandHub:

  def commandHub(store: EventStore): CommandHub =
    val state: IO[ToDoListState] = store.load.map(Replay.replayFrom(Events.empty, _))
    CommandHub(
      command =>
        state.flatMap { current =>
          Commands.handle(command, current) match
            case Right(events) => store.append(events).as(Right(()))
            case Left(error)   => IO.pure(Left(error))
        },
      state
    )
