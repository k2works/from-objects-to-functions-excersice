import { Effect } from "effect"
import { describe, expect, it } from "vitest"
import { announceScore } from "../src/announce-score.ts"

describe("Promise", () => {
  it("runs as soon as it is created, and only once", async () => {
    let runs = 0
    const promise = new Promise<void>((resolve) => {
      runs += 1
      resolve()
    })
    expect(runs).toBe(1)

    await promise
    await promise
    expect(runs).toBe(1)
  })
})

describe("announceScore", () => {
  it("does nothing until the Effect is run, and runs every time", async () => {
    const lines: Array<string> = []
    const announce = announceScore("Effect", (line) => {
      lines.push(line)
    })
    expect(lines).toEqual([])

    expect(await Effect.runPromise(announce)).toBe(6)
    expect(lines).toEqual(["Effect: 6"])

    await Effect.runPromise(announce)
    expect(lines).toEqual(["Effect: 6", "Effect: 6"])
  })
})
