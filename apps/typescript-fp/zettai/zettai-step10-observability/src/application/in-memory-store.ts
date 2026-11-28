import { Effect, Result } from "effect"
import type { ToDoListEvent } from "../domain/events.ts"
import type { ToDoListPorts } from "../domain/hub.ts"
import { belongsTo } from "../domain/streams.ts"

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
    // 読み取りから追記までを、1 つの同期の処理にします。途中で、ほかの処理は動きません。
    update: (streams, decide) =>
      Effect.suspend(() => {
        const decided = decide(events.filter((event) => belongsTo(event, streams)))
        if (Result.isSuccess(decided)) {
          if (!decided.success.every((event) => belongsTo(event, streams))) {
            return Effect.die(new Error("対象でないストリームへの追記です"))
          }
          events.push(...decided.success)
        }
        return Effect.fromResult(decided)
      })
  }
}
