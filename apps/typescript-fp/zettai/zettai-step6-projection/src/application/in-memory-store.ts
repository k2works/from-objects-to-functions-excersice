import { Effect } from "effect"
import type { ToDoListEvent } from "../domain/events.ts"
import type { ToDoListPorts } from "../domain/hub.ts"

// 出来事をメモリに持つ保管です。読み取りは、条件に合う出来事を追記順に返します。
export const makeInMemoryStore = (history: ReadonlyArray<ToDoListEvent>): ToDoListPorts => {
  // 呼び出し側の配列は書き換えないように、写しを持ちます。
  const events = [...history]
  const select = (matches: (event: ToDoListEvent) => boolean) =>
    Effect.sync(() => events.filter(matches))

  return {
    readListEvents: (user, listName) =>
      select((event) => event.user === user && event.listName === listName),
    readUserEvents: (user) => select((event) => event.user === user),
    persist: (added) =>
      Effect.sync(() => {
        events.push(...added)
      })
  }
}
