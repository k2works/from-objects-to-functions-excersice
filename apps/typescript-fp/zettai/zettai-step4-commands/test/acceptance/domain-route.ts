import { Effect, Option } from "effect"
import { makeInMemoryStore } from "../../src/application/in-memory-store.ts"
import { makeHub } from "../../src/domain/hub.ts"
import type { Route } from "./driver.ts"

// domain を直接呼ぶ経路です。サーバーも HTML もありません。
export const domainRoute: Route = (history, use) => {
  const hub = makeHub(makeInMemoryStore(history))
  return use({
    send: async (command) => {
      await Effect.runPromise(hub.execute(command))
    },
    itemsOf: (user, listName) =>
      Effect.runPromise(
        hub
          .getList(user, listName)
          .pipe(Effect.map(Option.map((list) => list.items.map((item) => item.description))))
      )
  })
}
