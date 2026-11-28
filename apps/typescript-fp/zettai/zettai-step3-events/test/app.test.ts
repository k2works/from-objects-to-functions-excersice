import { Effect } from "effect"
import { describe, expect, it } from "vitest"
import { findToDoList } from "../src/domain/find-todo-list.ts"
import { makeHub } from "../src/domain/hub.ts"
import { makeApp } from "../src/web/app.ts"
import { listOf, userOf } from "./support/model.ts"

const store = new Map([
  [userOf("uberto"), [listOf("book", "write chapter")]],
  [userOf("利用者"), [listOf("買い物 メモ", "牛乳")]]
])
const app = makeApp(makeHub(findToDoList(store)))

// サーバーは起動しません。Request を渡して、Response を受け取ります。
const call = async (path: string, method = "GET") => {
  const response = await Effect.runPromise(app(new Request(`http://localhost${path}`, { method })))
  return { status: response.status, body: await response.text() }
}

describe("GET /todo/{user}/{listname}", () => {
  it("returns the page of an existing list", async () => {
    const response = await call("/todo/uberto/book")

    expect(response.status).toBe(200)
    expect(response.body).toContain("<li>write chapter</li>")
  })

  it("decodes names written in Japanese and with spaces", async () => {
    const path = `/todo/${encodeURIComponent("利用者")}/${encodeURIComponent("買い物 メモ")}`

    const response = await call(path)

    expect(response.status).toBe(200)
    expect(response.body).toContain("<li>牛乳</li>")
  })

  it.each([
    ["an unknown list", "/todo/uberto/music"],
    ["an unknown user", "/todo/somebody/book"],
    ["an unknown path", "/nothing"],
    ["a missing list name", "/todo/uberto"],
    ["an empty list name", "/todo/uberto/"],
    ["an empty user name", "/todo//book"],
    ["an extra segment", "/todo/uberto/book/more"]
  ])("returns 404 for %s", async (_case, path) => {
    expect(await call(path)).toEqual({ status: 404, body: "not found" })
  })

  it.each(["POST", "PUT", "DELETE", "HEAD"])("returns 404 for %s", async (method) => {
    expect((await call("/todo/uberto/book", method)).status).toBe(404)
  })

  it("returns 400 for a broken percent encoding", async () => {
    expect(await call("/todo/uberto/%E0%A4%A")).toEqual({ status: 400, body: "bad request" })
  })
})
