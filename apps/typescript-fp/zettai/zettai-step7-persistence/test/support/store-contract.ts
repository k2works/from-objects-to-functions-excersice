import { Effect, Result } from "effect"
import fc from "fast-check"
import { expect, it } from "vitest"
import { itemAdded, itemStatusChanged, listCreated } from "../../src/domain/events.ts"
import type { ToDoListEvent } from "../../src/domain/events.ts"
import type { ToDoListPorts } from "../../src/domain/hub.ts"
import { makeToDoItem, parseDueDate } from "../../src/domain/todo-list.ts"
import { createdWith } from "./history.ts"
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
    await run(store.persist(history))
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
    await run(store.persist([itemAdded(uberto, book, makeToDoItem("second"))]))

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
