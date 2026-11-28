import { Effect, Result } from "effect"
import type { Option } from "effect"
import type { ToDoListCommand } from "./commands.ts"
import { listNotFound } from "./errors.ts"
import type { ListNotFound, ZettaiError } from "./errors.ts"
import type { ToDoListEvent } from "./events.ts"
import { handle } from "./handle.ts"
import type { ToDoListState } from "./state.ts"
import type { ListName, ToDoList, User } from "./todo-list.ts"

// ハブが外に頼る操作です。保管を読み書きするので、どれも実行するまで起きない Effect で表します。
export type ToDoListFetcher = (
  user: User,
  listName: ListName
) => Effect.Effect<Option.Option<ToDoList>>

export type StateFetcher = () => Effect.Effect<ToDoListState>

export type EventPersister = (events: ReadonlyArray<ToDoListEvent>) => Effect.Effect<void>

export interface ToDoListPorts {
  readonly fetchList: ToDoListFetcher
  readonly fetchState: StateFetcher
  readonly persist: EventPersister
}

// ドメインの入口です。web は、この型の操作だけを呼びます。
export interface ToDoListHub {
  // リストが無いことは、型に現れる失敗です。
  readonly getList: (user: User, listName: ListName) => Effect.Effect<ToDoList, ListNotFound>
  readonly execute: (
    command: ToDoListCommand
  ) => Effect.Effect<ReadonlyArray<ToDoListEvent>, ZettaiError>
}

// 依存は、関数の引数で受け取ります。
export const makeHub = ({ fetchList, fetchState, persist }: ToDoListPorts): ToDoListHub => ({
  // アダプタは「あるか無いか」を返します。失敗の理由を付けるのは、domain です。
  getList: (user, listName) =>
    fetchList(user, listName).pipe(
      Effect.flatMap((found) =>
        Effect.fromResult(Result.fromOption(found, () => listNotFound(user, listName)))
      )
    ),
  // 状態を取り出し、handle で決め、受け付けた出来事だけを保存します。
  execute: (command) =>
    fetchState().pipe(
      Effect.flatMap((state) => Effect.fromResult(handle(command, state))),
      Effect.tap(persist)
    )
})
