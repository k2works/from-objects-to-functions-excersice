import { Effect, Option } from "effect"
import { describe, expect, it } from "vitest"
import { findToDoList } from "../src/domain/find-todo-list.ts"
import { makeHub } from "../src/domain/hub.ts"
import { makeToDoItem } from "../src/domain/todo-list.ts"
import { makeApp } from "../src/web/app.ts"
import { listNameOf, userOf } from "./support/model.ts"

const get = async (app: ReturnType<typeof makeApp>, path: string) => {
  const response = await Effect.runPromise(app(new Request(`http://localhost${path}`)))
  return { status: response.status, body: await response.text() }
}

describe("ToDoListHub", () => {
  it("asks the fetcher for the list of the user", () => {
    const asked: Array<ReadonlyArray<string>> = []
    const hub = makeHub((user, listName) => {
      asked.push([user, listName])
      return Option.none()
    })

    hub.getList(userOf("uberto"), listNameOf("book"))

    expect(asked).toEqual([["uberto", "book"]])
  })

  it("lets a test show a page without any store or server", async () => {
    const hub = makeHub((_user, listName) =>
      Option.some({ listName, items: [makeToDoItem("from the test")] })
    )

    const response = await get(makeApp(hub), "/todo/anyone/anything")

    expect(response.status).toBe(200)
    expect(response.body).toContain("<li>from the test</li>")
  })
})

// 型検査で拒否される書き方です。実行はしません。
export const missingDependencies = () => {
  // @ts-expect-error 取得の依存を渡さないと、ハブを作れません
  makeHub()
  // @ts-expect-error ハブを渡さないと、App を作れません
  makeApp()
  // @ts-expect-error web が受け取るのはハブです。取得の依存は受け取りません
  makeApp(findToDoList(new Map()))
}
