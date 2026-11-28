import { Effect } from "effect"
import type { ToDoListEvent } from "../domain/events.ts"
import type { ToDoListPorts } from "../domain/hub.ts"
import { replay } from "../domain/replay.ts"
import { listIn } from "../domain/state.ts"

// 出来事をメモリに持つ保管です。状態は持たず、読むたびに出来事を畳み込みます。
export const makeInMemoryStore = (history: ReadonlyArray<ToDoListEvent>): ToDoListPorts => {
  // 呼び出し側の配列は書き換えないように、写しを持ちます。
  const events = [...history]
  const fetchState = () => Effect.sync(() => replay(events))

  return {
    fetchList: (user, listName) =>
      fetchState().pipe(Effect.map((state) => listIn(state, user, listName))),
    fetchState,
    persist: (added) =>
      Effect.sync(() => {
        events.push(...added)
      })
  }
}
