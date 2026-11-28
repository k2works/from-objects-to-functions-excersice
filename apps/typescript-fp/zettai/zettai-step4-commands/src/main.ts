import { Effect } from "effect"
import { parsePort } from "./application/port.ts"
import { makeInMemoryStore } from "./application/in-memory-store.ts"
import { sampleHistory } from "./application/sample-history.ts"
import { makeHub } from "./domain/hub.ts"
import { makeApp } from "./web/app.ts"
import { serve } from "./web/server.ts"

const program = Effect.gen(function* () {
  const port = yield* Effect.fromResult(parsePort(process.env["ZETTAI_PORT"]))
  const server = yield* serve(makeApp(makeHub(makeInMemoryStore(sampleHistory))), port)
  yield* Effect.sync(() => {
    console.log(`http://127.0.0.1:${server.port}/todo/uberto/book`)
  })
  return yield* Effect.never
})

// アプリケーションで Effect を実行するのは、ここと、server.ts の要求ごとの実行だけです。
await Effect.runPromise(Effect.scoped(program))
