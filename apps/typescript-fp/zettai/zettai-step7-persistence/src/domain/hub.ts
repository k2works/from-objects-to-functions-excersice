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
import type { ListName, ToDoList, User } from "./todo-list.ts"

// ハブが外に頼る操作は、出来事の読み取りと追記だけです。状態と行は、ハブが出来事から作ります。
export type ListEventsReader = (
  user: User,
  listName: ListName
) => Effect.Effect<ReadonlyArray<ToDoListEvent>>

export type UserEventsReader = (user: User) => Effect.Effect<ReadonlyArray<ToDoListEvent>>

export type EventPersister = (events: ReadonlyArray<ToDoListEvent>) => Effect.Effect<void>

export interface ToDoListPorts {
  readonly readListEvents: ListEventsReader
  readonly readUserEvents: UserEventsReader
  readonly persist: EventPersister
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
export const makeHub = ({
  readListEvents,
  readUserEvents,
  persist
}: ToDoListPorts): ToDoListHub => {
  const stateOf = (user: User, listName: ListName) =>
    readListEvents(user, listName).pipe(Effect.map(replay))

  return {
    // 対象のリストの出来事だけから状態を作り、handle で決めて、受け付けた出来事を追記します。
    execute: (command) =>
      stateOf(command.user, command.listName).pipe(
        Effect.flatMap((state) => Effect.fromResult(handle(command, state))),
        Effect.tap(persist)
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
