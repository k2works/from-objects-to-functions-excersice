import { Effect } from "effect"
import { sampleLists } from "./application/sample-lists.ts"
import { findToDoList } from "./domain/find-todo-list.ts"
import { makeHub } from "./domain/hub.ts"
import { makeApp } from "./web/app.ts"
import { serve } from "./web/server.ts"

// ビルドした出力でサーバーを起動し、要求を 1 回送って停止します。
const program = Effect.gen(function* () {
  const server = yield* serve(makeApp(makeHub(findToDoList(sampleLists))), 0)
  const body = yield* Effect.promise(async () => {
    const response = await fetch(`http://127.0.0.1:${server.port}/todo/uberto/book`)
    return `${response.status} ${await response.text()}`
  })
  if (!body.startsWith("200 ") || !body.includes("<li>write chapter</li>")) {
    return yield* Effect.die(new Error(`unexpected response: ${body}`))
  }
  return "smoke: 200 /todo/uberto/book"
})

const message = await Effect.runPromise(Effect.scoped(program))
console.log(message)
