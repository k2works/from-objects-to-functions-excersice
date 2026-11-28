import { Effect, Option } from "effect"
import { makeInMemoryStore } from "../../src/application/in-memory-store.ts"
import type { ToDoListEvent } from "../../src/domain/events.ts"
import { makeHub } from "../../src/domain/hub.ts"
import type { ToDoListHub, ToDoListPorts } from "../../src/domain/hub.ts"
import type { ListName, User } from "../../src/domain/todo-list.ts"
import { makeApp } from "../../src/web/app.ts"
import { serve } from "../../src/web/server.ts"
import type { Route, ZettaiDriver } from "./driver.ts"
import { readListPage, readRowsPage } from "./page.ts"

// 画面からの操作は、まだありません。コマンドはハブに送り、結果は HTTP で読みます。
const driverFor = (hub: ToDoListHub, port: number): ZettaiDriver => ({
  send: async (command) => {
    await Effect.runPromise(hub.execute(command))
  },
  listsOf: async (user: User) => {
    const response = await fetch(`http://127.0.0.1:${port}/todo/${encodeURIComponent(user)}`)
    if (response.status !== 200) throw new Error(`unexpected status ${response.status}`)
    const page = readRowsPage(await response.text())
    if (page.user !== user) throw new Error(`unexpected page of ${page.user}`)
    return page.rows
  },
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

// 経路ごとに違うのは、出来事を入れた保管の作り方だけです。
export type StoreOf = (history: ReadonlyArray<ToDoListEvent>) => Promise<ToDoListPorts>

// 実サーバーを起動する経路です。処理が終わると、成功でも失敗でも停止します。
export const httpRouteOn =
  (storeOf: StoreOf): Route =>
  async (history, use) => {
    const ports = await storeOf(history)
    return Effect.runPromise(
      Effect.scoped(
        Effect.gen(function* () {
          // コマンドを送る先と、サーバーが読む先は、同じハブです。
          const hub = makeHub(ports)
          const server = yield* serve(makeApp(hub), 0)
          return yield* Effect.promise(() => use(driverFor(hub, server.port)))
        })
      )
    )
  }

export const httpRoute: Route = httpRouteOn(async (history) => makeInMemoryStore(history))
