import { Effect, Result } from "effect"
import { describe, expect, it } from "vitest"
import { makeInMemoryStore } from "../src/application/in-memory-store.ts"
import type { ZettaiError } from "../src/domain/errors.ts"
import { makeHub } from "../src/domain/hub.ts"
import type { ToDoListHub } from "../src/domain/hub.ts"
import { failureResponse, makeApp } from "../src/web/app.ts"
import { serve } from "../src/web/server.ts"
import { createdWith } from "./support/history.ts"
import { send } from "./support/http.ts"
import { listNameOf, listOf, userOf } from "./support/model.ts"

const user = userOf("secret-user")
const listName = listNameOf("secret-list")

const businessFailures: ReadonlyArray<readonly [ZettaiError, number, string]> = [
  [{ _tag: "ListNotFound", user, listName }, 404, "not found"],
  [{ _tag: "ItemNotFound", user, listName, description: "secret-item" }, 404, "not found"],
  [{ _tag: "ListAlreadyExists", user, listName }, 400, "bad request"],
  [
    { _tag: "InvalidTransition", description: "secret-item", from: "Done", to: "Todo" },
    400,
    "bad request"
  ]
]

describe("failureResponse", () => {
  it.each(businessFailures)("maps %j to %i", async (error, status, body) => {
    const response = failureResponse(error._tag)

    expect(response.status).toBe(status)
    // 本文は固定です。失敗の中身は出しません。
    expect(await response.text()).toBe(body)
  })
})

describe("ToDoListHub getList", () => {
  const hub = makeHub(makeInMemoryStore(createdWith("uberto", "book", "write")))
  const get = (name: string) =>
    Effect.runPromise(Effect.result(hub.getList(userOf("uberto"), listNameOf(name))))

  it("returns the list", async () => {
    expect(await get("book")).toEqual(Result.succeed(listOf("book", "write")))
  })

  it("fails with the reason when there is no list", async () => {
    expect(await get("music")).toEqual(
      Result.fail({ _tag: "ListNotFound", user: "uberto", listName: "music" })
    )
  })
})

// 実サーバーに要求を送り、応答を受け取ります。
const request = (hub: ToDoListHub, path: string) =>
  Effect.runPromise(
    Effect.scoped(
      Effect.gen(function* () {
        const server = yield* serve(makeApp(hub), 0)
        return yield* Effect.promise(() => send(server.port, path))
      })
    )
  )

describe("a business failure and a defect", () => {
  const store = makeInMemoryStore([])
  const broken = (readListEvents: () => Effect.Effect<never>) =>
    makeHub({ ...store, readListEvents })

  it("answers a business failure with its own status, not 500", async () => {
    const response = await request(makeHub(store), "/todo/uberto/book")

    expect(response).toMatchObject({ status: 404, body: "not found" })
  })

  it.each([
    ["a defect of the adapter", () => Effect.die(new Error("password=secret"))],
    [
      "a bug thrown in the adapter",
      () =>
        Effect.sync((): never => {
          throw new TypeError("password=secret")
        })
    ]
  ])("answers %s with 500 without the reason", async (_name, read) => {
    const response = await request(broken(read), "/todo/uberto/book")

    expect(response).toMatchObject({ status: 500, body: "internal server error" })
  })
})
