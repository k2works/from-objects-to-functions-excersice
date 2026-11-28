import { Cause, Effect, Exit, Result } from "effect"
import { afterAll, beforeAll, describe, expect, it } from "vitest"
import { itemAdded, listCreated } from "../src/domain/events.ts"
import { makeHub } from "../src/domain/hub.ts"
import { streamOf } from "../src/domain/streams.ts"
import { makeToDoItem } from "../src/domain/todo-list.ts"
import { makeApp } from "../src/web/app.ts"
import { serve } from "../src/web/server.ts"
import { startDatabase } from "./support/database.ts"
import type { TestDatabase } from "./support/database.ts"
import { createdWith } from "./support/history.ts"
import { send } from "./support/http.ts"
import { seed } from "./support/seed.ts"
import { listNameOf, userOf } from "./support/model.ts"
import { storeContract } from "./support/store-contract.ts"

const uberto = userOf("uberto")
const book = listNameOf("book")

// Docker が無い場合は、skip せずに失敗します。
let database: TestDatabase
beforeAll(async () => {
  database = await startDatabase()
}, 120_000)
afterAll(async () => {
  await database.stop()
})

const run = Effect.runPromise
const isDefect = (exit: Exit.Exit<unknown, unknown>) =>
  Exit.isFailure(exit) && exit.cause.reasons.every(Cause.isDieReason)
// 返し忘れた接続の数です。
const inUse = () => database.pool.totalCount - database.pool.idleCount

describe("the PostgreSQL store", () => {
  storeContract(() => database.emptyStore())
})

describe("updating", () => {
  const target = [streamOf(uberto, book)]
  // 2 件目は、PostgreSQL が受け付けない NUL を含みます。
  const batch = [
    listCreated(uberto, book),
    itemAdded(uberto, book, makeToDoItem("bad \u0000 text"))
  ]
  const saveBatch = () => Result.succeed(batch)

  it("keeps nothing of a batch when one event cannot be saved", async () => {
    const store = await database.emptyStore()

    const exit = await Effect.runPromiseExit(store.update(target, saveBatch))

    expect(isDefect(exit)).toBe(true)
    expect(await run(store.readUserEvents(uberto))).toEqual([])
  })

  it("returns the connection after a success and after a defect", async () => {
    const store = await database.emptyStore()

    await seed(store, createdWith("uberto", "book"))
    await Effect.runPromiseExit(store.update(target, saveBatch))
    await run(store.readUserEvents(uberto))

    expect(inUse()).toBe(0)
  })

  // 業務の失敗は、接続の故障ではありません。接続を捨てると、拒否のたびに接続し直します。
  it("keeps the connection when the decision fails", async () => {
    const store = await database.emptyStore()
    await run(store.readUserEvents(uberto))
    const connections = database.pool.totalCount

    await run(Effect.result(store.update(target, () => Result.fail("no"))))

    // 捨てた接続は、この時点で数から減ります。
    expect(database.pool.totalCount).toBe(connections)
    expect(inUse()).toBe(0)
  })

  // 2 つのストリームを逆の順で渡しても、ロックを取る順がそろえば、行き詰まりません。
  it("does not deadlock when two updates name the same streams in the opposite order", async () => {
    const store = await database.emptyStore()
    const a = streamOf(uberto, listNameOf("a"))
    const b = streamOf(uberto, listNameOf("b"))
    const nothing = () => Result.succeed([])

    for (let round = 0; round < 30; round += 1) {
      await run(
        Effect.all([store.update([a, b], nothing), store.update([b, a], nothing)], {
          concurrency: "unbounded"
        })
      )
    }

    expect(inUse()).toBe(0)
  })

  it("keeps nothing and returns the connection when it is interrupted", async () => {
    const store = await database.emptyStore()
    // ほかの接続が表を押さえている間は、追記が待たされます。
    const blocker = await database.pool.connect()
    await blocker.query("BEGIN")
    await blocker.query("LOCK TABLE todo_list_event IN SHARE MODE")
    let released = false
    const unlock = async () => {
      if (released) return
      released = true
      await blocker.query("COMMIT")
      blocker.release()
    }
    const timer = setTimeout(() => void unlock(), 600)

    const exit = await Effect.runPromiseExit(
      store
        .update(target, () => Result.succeed(createdWith("uberto", "book")))
        .pipe(Effect.timeout(100))
    )
    clearTimeout(timer)
    await unlock()

    expect(Exit.isFailure(exit)).toBe(true)
    expect(await run(store.readUserEvents(uberto))).toEqual([])
    expect(inUse()).toBe(0)
  })
})

describe("broken rows", () => {
  it.each([
    ["an unknown event type", "ListDeleted", { user: "uberto", listName: "book" }],
    ["a missing value", "ItemAdded", { user: "uberto", listName: "book" }],
    [
      "an unknown status",
      "ItemStatusChanged",
      { user: "uberto", listName: "book", description: "x", newStatus: "Finished" }
    ],
    ["an empty name", "ListCreated", { user: "", listName: "book" }],
    [
      "a date that does not exist",
      "ItemAdded",
      {
        user: "uberto",
        listName: "book",
        item: { description: "x", status: "Todo", dueDate: "2026-02-30" }
      }
    ]
  ])("fails to read %s, and does not drop it", async (_name, eventType, payload) => {
    const store = await database.emptyStore()
    await seed(store, createdWith("uberto", "book"))
    await database.pool.query(
      "INSERT INTO todo_list_event (entity_id, event_type, payload) VALUES ($1, $2, $3)",
      ["uberto/book", eventType, JSON.stringify(payload)]
    )

    const exit = await Effect.runPromiseExit(store.readListEvents(uberto, book))

    expect(isDefect(exit)).toBe(true)
  })
})

describe("a database failure over HTTP", () => {
  it("answers 500 without the reason", async () => {
    const store = await database.emptyStore()
    await database.pool.query("ALTER TABLE todo_list_event RENAME TO hidden")
    try {
      const response = await run(
        Effect.scoped(
          Effect.gen(function* () {
            const server = yield* serve(makeApp(makeHub(store)), 0)
            return yield* Effect.promise(() => send(server.port, "/todo/uberto/book"))
          })
        )
      )

      expect(response).toMatchObject({ status: 500, body: "internal server error" })
    } finally {
      await database.pool.query("ALTER TABLE hidden RENAME TO todo_list_event")
    }
  })
})
