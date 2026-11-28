import { Effect, Option } from "effect"
import { findToDoList } from "../../src/domain/find-todo-list.ts"
import { makeHub } from "../../src/domain/hub.ts"
import type { ListName, User } from "../../src/domain/todo-list.ts"
import { makeApp } from "../../src/web/app.ts"
import { serve } from "../../src/web/server.ts"
import type { Route, ZettaiDriver } from "./driver.ts"
import { readListPage } from "./page.ts"

const driverFor = (port: number): ZettaiDriver => ({
  itemsOf: async (user: User, listName: ListName) => {
    const path = `/todo/${encodeURIComponent(user)}/${encodeURIComponent(listName)}`
    const response = await fetch(`http://127.0.0.1:${port}${path}`)
    if (response.status === 404) return Option.none()
    // 200 と 404 のほかは失敗にします。500 を「リストが無い」と読むと、壊れていても通ります。
    if (response.status !== 200) throw new Error(`unexpected status ${response.status}`)
    const page = readListPage(await response.text())
    if (page.name !== listName) throw new Error(`unexpected page of ${page.name}`)
    return Option.some(page.items)
  }
})

// 実サーバーを起動する経路です。処理が終わると、成功でも失敗でも停止します。
export const httpRoute: Route = (store, use) =>
  Effect.runPromise(
    Effect.scoped(
      Effect.gen(function* () {
        const server = yield* serve(makeApp(makeHub(findToDoList(store))), 0)
        return yield* Effect.promise(() => use(driverFor(server.port)))
      })
    )
  )
