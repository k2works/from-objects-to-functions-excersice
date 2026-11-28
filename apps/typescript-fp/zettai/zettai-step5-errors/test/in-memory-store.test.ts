import { Effect, Option } from "effect"
import { describe, expect, it } from "vitest"
import { makeInMemoryStore } from "../src/application/in-memory-store.ts"
import { createdWith } from "./support/history.ts"
import { listNameOf, listOf, userOf } from "./support/model.ts"

const { fetchList } = makeInMemoryStore(createdWith("uberto", "book", "write chapter"))
const fetch = (user: string, listName: string) =>
  Effect.runPromise(fetchList(userOf(user), listNameOf(listName)))

describe("makeInMemoryStore fetchList", () => {
  it("finds a list by the values of the names", async () => {
    expect(await fetch("uberto", "book")).toEqual(Option.some(listOf("book", "write chapter")))
  })

  it("returns none for an unknown list", async () => {
    expect(await fetch("uberto", "music")).toEqual(Option.none())
  })

  it("returns none for an unknown user", async () => {
    expect(await fetch("somebody", "book")).toEqual(Option.none())
  })
})
