import { Effect, Result } from "effect"
import type { ToDoListCommand } from "./commands.ts"
import { listNotFound } from "./errors.ts"
import type { ListNotFound, ZettaiError } from "./errors.ts"
import type { ToDoListEvent } from "./events.ts"
import { handle } from "./handle.ts"
import { projectRows } from "./projection.ts"
import type { ToDoListRow } from "./projection.ts"
import { replay } from "./replay.ts"
import { listIn } from "./state.ts"
import { streamOf } from "./streams.ts"
import type { StreamId } from "./streams.ts"
import type { ListName, ToDoList, User } from "./todo-list.ts"

// ハブが外に頼る操作は、出来事の読み取りと更新だけです。状態と行は、ハブが出来事から作ります。
export type ListEventsReader = (
  user: User,
  listName: ListName
) => Effect.Effect<ReadonlyArray<ToDoListEvent>>

export type UserEventsReader = (user: User) => Effect.Effect<ReadonlyArray<ToDoListEvent>>

// 履歴から、追記する出来事か、断る理由を決める純粋な関数です。
export type Decide<E> = (
  history: ReadonlyArray<ToDoListEvent>
) => Result.Result<ReadonlyArray<ToDoListEvent>, E>

// 対象のストリームを読み、decide で決め、追記するまでを、ほかの更新を挟まずに行います。
export type EventUpdater = <E>(
  streams: ReadonlyArray<StreamId>,
  decide: Decide<E>
) => Effect.Effect<ReadonlyArray<ToDoListEvent>, E>

export interface ToDoListPorts {
  readonly readListEvents: ListEventsReader
  readonly readUserEvents: UserEventsReader
  readonly update: EventUpdater
}

// ドメインの入口です。command は状態を変え、query は読むだけです。
export interface ToDoListHub {
  readonly execute: (
    command: ToDoListCommand
  ) => Effect.Effect<ReadonlyArray<ToDoListEvent>, ZettaiError>
  // リストが無いことは、型に現れる失敗です。
  readonly getList: (user: User, listName: ListName) => Effect.Effect<ToDoList, ListNotFound>
  readonly listRows: (user: User) => Effect.Effect<ReadonlyArray<ToDoListRow>>
}

// 依存は、関数の引数で受け取ります。
export const makeHub = ({ readListEvents, readUserEvents, update }: ToDoListPorts): ToDoListHub => {
  const stateOf = (user: User, listName: ListName) =>
    readListEvents(user, listName).pipe(Effect.map(replay))

  return {
    // 判断は保管に渡します。読み取りから追記までの間に、同じリストの更新は入れません。
    execute: (command) =>
      update([streamOf(command.user, command.listName)], (history) =>
        handle(command, replay(history))
      ),
    getList: (user, listName) =>
      stateOf(user, listName).pipe(
        Effect.flatMap((state) =>
          Effect.fromResult(
            Result.fromOption(listIn(state, user, listName), () => listNotFound(user, listName))
          )
        )
      ),
    // query は command の状態を通らず、出来事から画面の形を直接作ります。
    listRows: (user) => readUserEvents(user).pipe(Effect.map((events) => projectRows(user, events)))
  }
}
