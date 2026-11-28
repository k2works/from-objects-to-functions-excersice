import { Effect, Result } from "effect"
import { describe, expect, it } from "vitest"
import { makeInMemoryStore } from "../src/application/in-memory-store.ts"
import { addToDoItem, createToDoList } from "../src/domain/commands.ts"
import { itemAdded, listCreated } from "../src/domain/events.ts"
import type { ToDoListEvent } from "../src/domain/events.ts"
import { makeHub } from "../src/domain/hub.ts"
import type { ToDoListPorts } from "../src/domain/hub.ts"
import { makeToDoItem } from "../src/domain/todo-list.ts"
import { makeApp } from "../src/web/app.ts"
import { createdWith } from "./support/history.ts"
import { listNameOf, listOf, userOf } from "./support/model.ts"

const uberto = userOf("uberto")
const book = listNameOf("book")

// 呼ばれた内容を記録するアダプタです。状態は、常に空です。
const recording = () => {
  const saved: Array<ReadonlyArray<ToDoListEvent>> = []
  const ports: ToDoListPorts = {
    readListEvents: () => Effect.succeed([]),
    readUserEvents: () => Effect.succeed([]),
    persist: (events) =>
      Effect.sync(() => {
        saved.push(events)
      })
  }
  return { saved, hub: makeHub(ports) }
}

describe("ToDoListHub execute", () => {
  it("saves the events decided from the current state, and returns them", async () => {
    const { saved, hub } = recording()

    const events = await Effect.runPromise(hub.execute(createToDoList(uberto, book)))

    expect(events).toEqual([listCreated(uberto, book)])
    expect(saved).toEqual([events])
  })

  it("saves nothing when the command is rejected", async () => {
    const { saved, hub } = recording()

    const decided = await Effect.runPromise(
      Effect.result(hub.execute(addToDoItem(uberto, book, makeToDoItem("x"))))
    )

    expect(decided).toEqual(Result.fail({ _tag: "ListNotFound", user: "uberto", listName: "book" }))
    expect(saved).toEqual([])
  })

  it("does nothing until the Effect is run", () => {
    const { saved, hub } = recording()

    const notRun = hub.execute(createToDoList(uberto, book))

    expect(Effect.isEffect(notRun)).toBe(true)
    expect(saved).toEqual([])
  })
})

describe("ToDoListHub with the in-memory store", () => {
  it("shows what the accepted commands have changed", async () => {
    const hub = makeHub(makeInMemoryStore([]))
    const program = Effect.gen(function* () {
      yield* hub.execute(createToDoList(uberto, book))
      yield* hub.execute(addToDoItem(uberto, book, makeToDoItem("write")))
      return yield* hub.getList(uberto, book)
    })

    expect(await Effect.runPromise(program)).toEqual(listOf("book", "write"))
  })

  it("decides the next command from the saved events", async () => {
    const hub = makeHub(makeInMemoryStore(createdWith("uberto", "book")))

    const decided = await Effect.runPromise(
      Effect.result(hub.execute(createToDoList(uberto, book)))
    )

    expect(Result.isFailure(decided) && decided.failure._tag).toBe("ListAlreadyExists")
  })

  it("does not share the events given to it with the caller", async () => {
    const history = [...createdWith("uberto", "book")]
    const hub = makeHub(makeInMemoryStore(history))

    await Effect.runPromise(hub.execute(addToDoItem(uberto, book, makeToDoItem("write"))))

    expect(history).toHaveLength(1)
  })
})

describe("commands and queries", () => {
  // どちらの口を呼んだかを記録します。
  const traced = () => {
    const calls: Array<string> = []
    const store = makeInMemoryStore(createdWith("uberto", "book", "write"))
    const hub = makeHub({
      readListEvents: (user, listName) => {
        calls.push(`list ${user}/${listName}`)
        return store.readListEvents(user, listName)
      },
      readUserEvents: (user) => {
        calls.push(`user ${user}`)
        return store.readUserEvents(user)
      },
      persist: store.persist
    })
    return { calls, hub }
  }

  it("reads only the events of the target list to decide a command", async () => {
    const { calls, hub } = traced()

    await Effect.runPromise(hub.execute(createToDoList(uberto, listNameOf("music"))))

    expect(calls).toEqual(["list uberto/music"])
  })

  it("projects the rows from the events of the user", async () => {
    const { calls, hub } = traced()

    const rows = await Effect.runPromise(hub.listRows(uberto))

    expect(rows).toEqual([{ listName: "book", itemCount: 1, doneCount: 0 }])
    expect(calls).toEqual(["user uberto"])
  })
})

describe("makeApp", () => {
  it("lets a test show a page without any store or server", async () => {
    const hub = makeHub({
      ...makeInMemoryStore([]),
      readListEvents: (user, listName) =>
        Effect.succeed([
          listCreated(user, listName),
          itemAdded(user, listName, makeToDoItem("from the test"))
        ])
    })

    const response = await Effect.runPromise(
      makeApp(hub)(new Request("http://localhost/todo/anyone/anything"))
    )

    expect(await response.text()).toContain("<li>from the test</li>")
  })
})

// 型検査で拒否される書き方です。実行はしません。
export const missingDependencies = () => {
  const { readListEvents, readUserEvents } = makeInMemoryStore([])
  // @ts-expect-error 依存を渡さないと、ハブを作れません
  makeHub()
  // @ts-expect-error 保存の依存が足りません
  makeHub({ readListEvents, readUserEvents })
  // @ts-expect-error ハブを渡さないと、App を作れません
  makeApp()
  // @ts-expect-error web が受け取るのはハブです。保管は受け取りません
  makeApp(makeInMemoryStore([]))
}
