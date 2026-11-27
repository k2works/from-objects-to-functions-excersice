import { Effect } from "effect"
import { expect, it } from "vitest"
import { findToDoList } from "../src/domain/find-todo-list.ts"
import { makeApp } from "../src/web/app.ts"
import { serve } from "../src/web/server.ts"
import { send } from "./support/http.ts"

it("serves the items of a ToDo list over real HTTP", async () => {
  const store = new Map([
    ["uberto", [{ listName: { name: "book" }, items: [{ description: "write chapter" }] }]]
  ])
  const program = Effect.gen(function* () {
    const server = yield* serve(makeApp(findToDoList(store)), 0)
    return yield* Effect.promise(() => send(server.port, "/todo/uberto/book"))
  })

  const response = await Effect.runPromise(Effect.scoped(program))

  expect(response.status).toBe(200)
  expect(response.contentType).toBe("text/html; charset=utf-8")
  expect(response.body).toContain("<li>write chapter</li>")
})
