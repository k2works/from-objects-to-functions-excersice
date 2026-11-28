import { Effect, Result } from "effect"
import fc from "fast-check"
import { describe, expect, it } from "vitest"
import { loggedApp, loggedPorts } from "../src/adapter/logging/logged.ts"
import type { Logging } from "../src/adapter/logging/logged.ts"
import { makeInMemoryStore } from "../src/application/in-memory-store.ts"
import { addToDoItem, createToDoList, renameToDoList } from "../src/domain/commands.ts"
import { makeHub } from "../src/domain/hub.ts"
import { makeToDoItem } from "../src/domain/todo-list.ts"
import { makeApp } from "../src/web/app.ts"
import { createdWith } from "./support/history.ts"
import { listNameOf, userOf } from "./support/model.ts"

const TRIALS = 200

const uberto = userOf("uberto")
const book = listNameOf("book")

// 書いた行を集めます。時刻は、呼ぶたびに 5 ミリ秒進みます。
const collecting = () => {
  const lines: Array<string> = []
  let time = 0
  const logging: Logging = {
    write: (line) => {
      lines.push(line)
    },
    now: () => (time += 5)
  }
  return { lines, logging, records: () => lines.map((line): unknown => JSON.parse(line)) }
}

const run = <A, E>(effect: Effect.Effect<A, E>) => Effect.runPromise(Effect.result(effect))

describe("logged store operations", () => {
  it("writes one line for a successful update", async () => {
    const { logging, records } = collecting()
    const hub = makeHub(loggedPorts(makeInMemoryStore([]), logging))

    await run(hub.execute(createToDoList(uberto, book)))

    expect(records()).toEqual([
      {
        operation: "update",
        streams: [{ user: "uberto", listName: "book" }],
        outcome: "success",
        eventCount: 1,
        durationMs: 5
      }
    ])
  })

  it("records the kind of a business failure, not its content", async () => {
    const { logging, records } = collecting()
    const hub = makeHub(loggedPorts(makeInMemoryStore(createdWith("uberto", "book")), logging))

    await run(hub.execute(createToDoList(uberto, book)))

    expect(records()).toEqual([expect.objectContaining({ outcome: "ListAlreadyExists" })])
  })

  it("records the class of a defect, and still dies", async () => {
    const { lines, logging, records } = collecting()
    const store = makeInMemoryStore([])
    const broken = loggedPorts(
      { ...store, readUserEvents: () => Effect.die(new TypeError("password=secret")) },
      logging
    )

    const exit = await Effect.runPromiseExit(broken.readUserEvents(uberto))

    expect(exit._tag).toBe("Failure")
    expect(records()).toEqual([expect.objectContaining({ outcome: "defect:TypeError" })])
    expect(lines.join()).not.toContain("secret")
  })

  it("records an interruption", async () => {
    const { logging, records } = collecting()
    const store = makeInMemoryStore([])
    const slow = loggedPorts({ ...store, readUserEvents: () => Effect.never }, logging)

    await Effect.runPromiseExit(slow.readUserEvents(uberto).pipe(Effect.timeout(5)))

    expect(records()).toEqual([expect.objectContaining({ outcome: "interrupted" })])
  })

  it("writes exactly the chosen fields for the two reads", async () => {
    const { logging, records } = collecting()
    const ports = loggedPorts(makeInMemoryStore(createdWith("uberto", "book")), logging)

    await Effect.runPromise(ports.readListEvents(uberto, book))
    await Effect.runPromise(ports.readUserEvents(uberto))

    const common = { outcome: "success", eventCount: 1, durationMs: 5, user: "uberto" }
    expect(records()).toEqual([
      { operation: "readListEvents", listName: "book", ...common },
      { operation: "readUserEvents", ...common }
    ])
  })

  // 型が StreamId でも、ほかの項目を持つオブジェクトを渡せます。ログは、決めた項目だけを書きます。
  it("writes only the user and the list name of a stream", async () => {
    const { lines, logging } = collecting()
    const ports = loggedPorts(makeInMemoryStore([]), logging)
    const wider = { user: uberto, listName: book, items: ["secret item"] }

    await Effect.runPromise(ports.update([wider], () => Result.succeed([])))

    expect(lines.join()).not.toContain("secret")
  })

  // 項目の説明は、どの操作のログにも現れません。生成した説明に目印を付けて確かめます。
  it("never writes the description of an item", async () => {
    await fc.assert(
      fc.asyncProperty(fc.string({ unit: "grapheme", maxLength: 12 }), async (text) => {
        const { lines, logging } = collecting()
        const hub = makeHub(loggedPorts(makeInMemoryStore([]), logging))
        const description = `MARK${text}MARK`

        await run(hub.execute(createToDoList(uberto, book)))
        await run(hub.execute(addToDoItem(uberto, book, makeToDoItem(description))))
        await run(hub.execute(renameToDoList(uberto, book, listNameOf("next"))))
        await run(hub.getList(uberto, listNameOf("next")))
        await run(hub.listRows(uberto))

        expect(lines.length).toBeGreaterThanOrEqual(5)
        expect(lines.join()).not.toContain("MARK")
      }),
      { numRuns: TRIALS }
    )
  })
})

describe("logged requests", () => {
  const call = async (target: ReturnType<typeof makeApp>, path: string, init?: RequestInit) =>
    Effect.runPromiseExit(target(new Request(`http://localhost${path}`, init)))

  it("writes the method, the path without the query, and the status", async () => {
    const { logging, records } = collecting()
    const app = loggedApp(makeApp(makeHub(makeInMemoryStore([]))), logging)

    await call(app, "/todo/uberto/book?token=secret")

    expect(records()).toEqual([
      {
        operation: "request",
        method: "GET",
        path: "/todo/uberto/book",
        outcome: "success",
        status: 404,
        durationMs: 5
      }
    ])
  })

  it("does not write the body of a form", async () => {
    const { lines, logging } = collecting()
    const history = createdWith("uberto", "book")
    const app = loggedApp(makeApp(makeHub(makeInMemoryStore(history))), logging)

    await call(app, "/todo/uberto/book/rename", {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: "newName=&memo=secret"
    })

    expect(lines).toHaveLength(1)
    expect(lines.join()).not.toContain("secret")
  })

  it("records a defect of the application", async () => {
    const { logging, records } = collecting()
    const app = loggedApp(() => Effect.die(new RangeError("secret")), logging)

    await call(app, "/x")

    expect(records()).toEqual([expect.objectContaining({ outcome: "defect:RangeError" })])
  })
})

// 出力先が失敗しても、本体の結果は変えません。
describe("a failing log output", () => {
  it("does not change the result of the operation", async () => {
    const logging: Logging = {
      write: () => {
        throw new Error("disk full")
      },
      now: () => 0
    }
    const hub = makeHub(loggedPorts(makeInMemoryStore([]), logging))

    const result = await run(hub.execute(createToDoList(uberto, book)))

    expect(Result.isSuccess(result)).toBe(true)
  })
})
