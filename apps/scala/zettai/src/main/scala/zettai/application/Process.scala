package zettai.application

import zettai.domain.commands.{Commands, ToDoListCommand}
import zettai.domain.errors.ZettaiError
import zettai.domain.events.{Events, Replay, ToDoListEvent, ToDoListState}

import cats.data.Kleisli
import cats.effect.IO

/** コマンドの処理が必要とする文脈（EventStore）を、引数ではなく Kleisli の環境として受け取る。 */
object Process:

  type Context[A] = Kleisli[IO, EventStore, A]

  /** 読む、判断する、書く。文脈は引数に現れず、型 Kleisli[IO, EventStore, _] にだけ現れる。 */
  def process(command: ToDoListCommand): Context[Either[ZettaiError, Unit]] =
    Kleisli { store =>
      store.load.flatMap { read =>
        val state = Replay.replayFrom(Events.empty, read.events)
        Commands.handle(command, state) match
          case Right(events) => store.append(read.version, events)
          case Left(error)   => IO.pure(Left(error))
      }
    }

  /**
   * 複数のコマンドを、1 つの単位として処理する。あとのコマンドは、前のコマンドが作った状態を見る。
   * 1 つでも拒否されたら何も書かず、すべて受け入れられたら、1 回の書き込みでまとめて書く。
   */
  def processAll(commands: List[ToDoListCommand]): Context[Either[ZettaiError, Unit]] =
    Kleisli { store =>
      store.load.flatMap { read =>
        val start: Either[ZettaiError, (ToDoListState, List[ToDoListEvent])] =
          Right((Replay.replayFrom(Events.empty, read.events), Nil))
        val decided = commands.foldLeft(start) { (acc, command) =>
          acc.flatMap { (state, written) =>
            Commands.handle(command, state).map(events => (Replay.replayFrom(state, events), written ::: events))
          }
        }
        decided match
          case Right((_, events)) => store.append(read.version, events)
          case Left(error)        => IO.pure(Left(error))
      }
    }
