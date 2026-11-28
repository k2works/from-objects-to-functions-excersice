import { Effect, Result } from "effect"
import { streamsOf } from "../../src/domain/streams.ts"
import type { ToDoListEvent } from "../../src/domain/events.ts"
import type { ToDoListPorts } from "../../src/domain/hub.ts"

// 準備の出来事を、判断を通さずに保管に入れます。
export const seed = (store: ToDoListPorts, history: ReadonlyArray<ToDoListEvent>) =>
  Effect.runPromise(store.update(streamsOf(history), () => Result.succeed(history)))
