import { Effect } from "effect"
import { loggedApp, loggedPorts } from "./adapter/logging/logged.ts"
import type { Logging } from "./adapter/logging/logged.ts"
import { createSchema, makePostgresStore, postgresPool } from "./adapter/postgres/event-store.ts"
import { makeInMemoryStore } from "./application/in-memory-store.ts"
import { parsePort } from "./application/port.ts"
import { sampleHistory } from "./application/sample-history.ts"
import { makeHub } from "./domain/hub.ts"
import { makeApp } from "./web/app.ts"
import { serve } from "./web/server.ts"

// 接続先があれば PostgreSQL を、無ければ見本の出来事を入れたメモリの保管を使います。
const store = (connectionString: string | undefined) =>
  connectionString === undefined
    ? Effect.succeed(makeInMemoryStore(sampleHistory))
    : postgresPool(connectionString).pipe(Effect.tap(createSchema), Effect.map(makePostgresStore))

// ログは、1 つの操作を 1 行の JSON にして、標準出力に書きます。
const logging: Logging = {
  write: (line) => {
    console.log(line)
  },
  now: () => Math.round(performance.now())
}

const program = Effect.gen(function* () {
  const port = yield* Effect.fromResult(parsePort(process.env["ZETTAI_PORT"]))
  const ports = yield* store(process.env["ZETTAI_DATABASE_URL"])
  const app = makeApp(makeHub(loggedPorts(ports, logging)))
  const server = yield* serve(loggedApp(app, logging), port)
  yield* Effect.sync(() => {
    console.log(`http://127.0.0.1:${server.port}/todo/uberto/book`)
  })
  return yield* Effect.never
})

// アプリケーションで Effect を実行するのは、ここと、server.ts の要求ごとの実行だけです。
await Effect.runPromise(Effect.scoped(program))
