import { Cause, Effect, Exit, Result } from "effect"
import fc from "fast-check"
import { expect, it } from "vitest"
import { itemAdded, itemStatusChanged, listCreated } from "../../src/domain/events.ts"
import type { ToDoListEvent } from "../../src/domain/events.ts"
import type { ToDoListPorts } from "../../src/domain/hub.ts"
import { createToDoList } from "../../src/domain/commands.ts"
import { handle } from "../../src/domain/handle.ts"
import { replay } from "../../src/domain/replay.ts"
import { streamOf } from "../../src/domain/streams.ts"
import { makeToDoItem, parseDueDate } from "../../src/domain/todo-list.ts"
import { createdWith } from "./history.ts"
import { seed } from "./seed.ts"
import { listNameOf, userOf } from "./model.ts"

const STORE_TRIALS = 25

// どの保管も満たすべき振る舞いです。メモリと PostgreSQL に、同じものを当てます。
// emptyStore は、何も入っていない保管を作ります。
export const storeContract = (emptyStore: () => Promise<ToDoListPorts>) => {
  const run = Effect.runPromise
  const uberto = userOf("uberto")
  const book = listNameOf("book")

  const filled = async (history: ReadonlyArray<ToDoListEvent>) => {
    const store = await emptyStore()
    await seed(store, history)
    return store
  }

  it("reads back every kind of event as it was saved, in order", async () => {
    const dueDate = Result.getOrThrow(parseDueDate("2026-12-01"))
    const history = [
      listCreated(uberto, book),
      itemAdded(uberto, book, makeToDoItem("牛乳 \u{1f95b} 'a' \"b\" \\ %", { status: "Blocked" })),
      itemAdded(uberto, book, makeToDoItem("publish", { dueDate })),
      itemStatusChanged(uberto, book, "publish", "InProgress")
    ]
    const store = await filled(history)

    expect(await run(store.readListEvents(uberto, book))).toEqual(history)
  })

  it("appends to what was saved before", async () => {
    const store = await filled(createdWith("uberto", "book", "first"))
    await seed(store, [itemAdded(uberto, book, makeToDoItem("second"))])

    expect(await run(store.readListEvents(uberto, book))).toEqual(
      createdWith("uberto", "book", "first", "second")
    )
  })

  // "a/b" の "c" と、"a" の "b/c" は、素朴につなぐと同じ文字列になります。% も確かめます。
  it("keeps apart names that contain the separator or a percent sign", async () => {
    const lists = [
      ["a/b", "c"],
      ["a", "b/c"],
      ["a%2Fb", "c"],
      ["a", "b"],
      ["a", "b%"]
    ] as const
    const store = await filled(lists.flatMap(([user, name]) => createdWith(user, name)))

    for (const [user, name] of lists) {
      expect(await run(store.readListEvents(userOf(user), listNameOf(name)))).toEqual(
        createdWith(user, name)
      )
    }
    expect(await run(store.readUserEvents(userOf("a")))).toEqual([
      ...createdWith("a", "b/c"),
      ...createdWith("a", "b"),
      ...createdWith("a", "b%")
    ])
  })

  it("has no events for an unknown list or user", async () => {
    const store = await filled(createdWith("uberto", "book"))

    expect(await run(store.readListEvents(uberto, listNameOf("music")))).toEqual([])
    expect(await run(store.readUserEvents(userOf("uber")))).toEqual([])
  })

  const target = [streamOf(uberto, book)]
  const added = itemAdded(uberto, book, makeToDoItem("new"))
  const isDefect = (exit: Exit.Exit<unknown, unknown>) =>
    Exit.isFailure(exit) && exit.cause.reasons.every(Cause.isDieReason)

  it("gives the decision the events of the target streams only, in order", async () => {
    const mine = createdWith("uberto", "book", "first")
    const store = await filled([...mine, ...createdWith("uberto", "music")])
    const seen: Array<ReadonlyArray<ToDoListEvent>> = []

    const decided = await run(
      store.update(target, (history) => {
        seen.push(history)
        return Result.succeed([added])
      })
    )

    expect(seen).toEqual([mine])
    expect(decided).toEqual([added])
    expect(await run(store.readListEvents(uberto, book))).toEqual([...mine, added])
  })

  it("appends nothing and returns the failure when the decision fails", async () => {
    const store = await filled(createdWith("uberto", "book"))

    const result = await run(Effect.result(store.update(target, () => Result.fail("no"))))

    expect(result).toEqual(Result.fail("no"))
    expect(await run(store.readListEvents(uberto, book))).toEqual(createdWith("uberto", "book"))
  })

  it("appends nothing when the decision throws", async () => {
    const store = await filled(createdWith("uberto", "book"))

    const exit = await Effect.runPromiseExit(
      store.update(target, () => {
        throw new TypeError("bug")
      })
    )

    expect(isDefect(exit)).toBe(true)
    expect(await run(store.readListEvents(uberto, book))).toEqual(createdWith("uberto", "book"))
  })

  // 読んでいないストリームへの追記は、守られないので、実装の誤りにします。
  it("refuses to append to a stream that was not given", async () => {
    const store = await filled([])

    const exit = await Effect.runPromiseExit(
      store.update(target, () => Result.succeed([listCreated(uberto, listNameOf("music"))]))
    )

    expect(isDefect(exit)).toBe(true)
    expect(await run(store.readUserEvents(uberto))).toEqual([])
  })

  it("accepts only one of the same commands run at the same time", async () => {
    for (let round = 0; round < 5; round += 1) {
      const store = await filled([])
      const command = createToDoList(uberto, book)
      const once = Effect.result(store.update(target, (h) => handle(command, replay(h))))

      const results = await run(Effect.all([once, once], { concurrency: "unbounded" }))

      expect(results.filter(Result.isSuccess)).toHaveLength(1)
      expect(await run(store.readListEvents(uberto, book))).toEqual([listCreated(uberto, book)])
    }
  })

  // 保存できる文字は、決まった例だけでは確かめきれません。
  it("reads back any well-formed description without a NUL", async () => {
    const description = fc
      .string({ unit: "grapheme", maxLength: 20 })
      .filter((text) => !text.includes("\u0000"))

    await fc.assert(
      fc.asyncProperty(description, async (text) => {
        const history = [listCreated(uberto, book), itemAdded(uberto, book, makeToDoItem(text))]
        const store = await filled(history)

        expect(await run(store.readListEvents(uberto, book))).toEqual(history)
      }),
      { numRuns: STORE_TRIALS }
    )
  })
}
