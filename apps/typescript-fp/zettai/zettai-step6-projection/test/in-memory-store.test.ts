import { Effect } from "effect"
import { describe, expect, it } from "vitest"
import { makeInMemoryStore } from "../src/application/in-memory-store.ts"
import { createdWith } from "./support/history.ts"
import { listNameOf, userOf } from "./support/model.ts"

const book = createdWith("uberto", "book", "write chapter")
const music = createdWith("uberto", "music")
const franks = createdWith("frank", "book", "water")
const store = makeInMemoryStore([...book, ...franks, ...music])

describe("makeInMemoryStore", () => {
  it("reads the events of one list of one user, in order", async () => {
    const events = store.readListEvents(userOf("uberto"), listNameOf("book"))

    expect(await Effect.runPromise(events)).toEqual(book)
  })

  it("reads the events of one user, in order", async () => {
    expect(await Effect.runPromise(store.readUserEvents(userOf("uberto")))).toEqual([
      ...book,
      ...music
    ])
  })

  it("has no events for an unknown user", async () => {
    expect(await Effect.runPromise(store.readUserEvents(userOf("somebody")))).toEqual([])
  })
})
