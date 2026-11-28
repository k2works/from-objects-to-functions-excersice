import { Option } from "effect"
import { findToDoList } from "../../src/domain/find-todo-list.ts"
import { makeHub } from "../../src/domain/hub.ts"
import { replay } from "../../src/domain/replay.ts"
import type { Route } from "./driver.ts"

// domain を直接呼ぶ経路です。サーバーも HTML もありません。
export const domainRoute: Route = (history, use) => {
  const hub = makeHub(findToDoList(replay(history)))
  return use({
    itemsOf: async (user, listName) =>
      hub
        .getList(user, listName)
        .pipe(Option.map((list) => list.items.map((item) => item.description)))
  })
}
