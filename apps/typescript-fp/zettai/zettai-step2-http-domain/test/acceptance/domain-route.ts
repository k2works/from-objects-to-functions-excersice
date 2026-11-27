import { Option } from "effect"
import { findToDoList } from "../../src/domain/find-todo-list.ts"
import type { Route } from "./driver.ts"

// domain を直接呼ぶ経路です。サーバーも HTML もありません。
export const domainRoute: Route = (store, use) => {
  const fetchList = findToDoList(store)
  return use({
    itemsOf: async (user, listName) =>
      fetchList(user, listName).pipe(
        Option.map((list) => list.items.map((item) => item.description))
      )
  })
}
