package zettai

import cats.effect.IO

/** コマンドの入口。実行すると、拒否の理由か、何も返さない成功を返す。 */
final case class CommandHub(
    execute: ToDoListCommand => IO[Either[ZettaiError, Unit]],
    /** 読んだ版を指定して実行する。指定の版が古ければ、書き込みが VersionConflict で断られる。 */
    executeAt: (Int, ToDoListCommand) => IO[Either[ZettaiError, Unit]],
    current: IO[ToDoListState]
)

object CommandHub:

  def commandHub(store: EventStore): CommandHub =
    val state: IO[ToDoListState] = store.load.map(read => Replay.replayFrom(Events.empty, read.events))

    def executeAt(expected: Int, command: ToDoListCommand): IO[Either[ZettaiError, Unit]] =
      state.flatMap { current =>
        Commands.handle(command, current) match
          case Right(events) => store.append(expected, events)
          case Left(error)   => IO.pure(Left(error))
      }

    CommandHub(
      command => store.load.flatMap(read => executeAt(read.version, command)),
      executeAt,
      state
    )
