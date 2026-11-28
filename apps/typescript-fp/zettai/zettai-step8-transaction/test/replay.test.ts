import fc from "fast-check"
import { describe, expect, it } from "vitest"
import { replay, replayRecursively } from "../src/domain/replay.ts"
import { emptyState } from "../src/domain/state.ts"
import { createdWith } from "./support/history.ts"
import { events } from "./support/arbitraries.ts"

const TRIALS = 500

describe("replay", () => {
  it("starts from the empty state", () => {
    expect(replay([])).toEqual(emptyState)
    expect(replayRecursively([])).toEqual(emptyState)
  })

  it("applies the events in order", () => {
    const history = createdWith("uberto", "book", "write chapter", "insert code")

    expect(replayRecursively(history)).toEqual(replay(history))
    expect([...replay(history).values()].flatMap((lists) => [...lists.values()])).toMatchObject([
      {
        listName: "book",
        items: [{ description: "write chapter" }, { description: "insert code" }]
      }
    ])
  })

  it("gives the same state by recursion and by reduce", () => {
    fc.assert(
      fc.property(events, (history) => {
        expect(replayRecursively(history)).toEqual(replay(history))
      }),
      { numRuns: TRIALS }
    )
  })
})
