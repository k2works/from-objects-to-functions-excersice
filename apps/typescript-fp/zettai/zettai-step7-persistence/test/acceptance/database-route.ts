import { Effect } from "effect"
import { startDatabase } from "../support/database.ts"
import type { TestDatabase } from "../support/database.ts"
import { httpRouteOn } from "./http-route.ts"

// 3 経路目です。HTTP の経路を、PostgreSQL の保管の上で実行します。
let database: TestDatabase | undefined

export const startRouteDatabase = async (): Promise<void> => {
  database = await startDatabase()
}

export const stopRouteDatabase = async (): Promise<void> => {
  await database?.stop()
}

export const databaseRoute = httpRouteOn(async (history) => {
  if (database === undefined) throw new Error("the database is not started")
  // 表を空にしてから、準備の出来事を保存します。
  const store = await database.emptyStore()
  await Effect.runPromise(store.persist(history))
  return store
})
