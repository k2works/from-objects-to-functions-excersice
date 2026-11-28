import { Effect } from "effect"
import { expect, it } from "vitest"
import { findToDoList } from "../src/domain/find-todo-list.ts"
import { makeHub } from "../src/domain/hub.ts"
import { replay } from "../src/domain/replay.ts"
import { makeApp } from "../src/web/app.ts"
import { serve } from "../src/web/server.ts"
import { send } from "./support/http.ts"
import { createdWith } from "./support/history.ts"

it("serves the items of a ToDo list over real HTTP", async () => {
  const state = replay(createdWith("uberto", "book", "write chapter"))
  const program = Effect.gen(function* () {
    const server = yield* serve(makeApp(makeHub(findToDoList(state))), 0)
    return yield* Effect.promise(() => send(server.port, "/todo/uberto/book"))
  })

  const response = await Effect.runPromise(Effect.scoped(program))

  expect(response.status).toBe(200)
  expect(response.contentType).toBe("text/html; charset=utf-8")
  expect(response.body).toContain("<li>write chapter</li>")
})
