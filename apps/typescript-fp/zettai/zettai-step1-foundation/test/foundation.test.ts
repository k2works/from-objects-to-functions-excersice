import { Effect, Layer, Result } from "effect"
import fc from "fast-check"
import { describe, expect, it } from "vitest"
import { Greeting, greet, parsePositiveInt, sum } from "../src/foundation.ts"

const TRIALS = 200
const numbers = fc.array(fc.integer())

describe("pure function", () => {
  it("adds up the numbers", () => {
    expect(sum([1, 2, 3])).toBe(6)
  })

  it("gives the same total when the list is split anywhere", () => {
    fc.assert(
      fc.property(numbers, numbers, (left, right) => {
        expect(sum([...left, ...right])).toBe(sum(left) + sum(right))
      }),
      { numRuns: TRIALS }
    )
  })
})

describe("Result", () => {
  it("keeps a positive integer as a success", () => {
    expect(parsePositiveInt("42")).toEqual(Result.succeed(42))
  })

  it("returns the rejected input as a failure", () => {
    expect(parsePositiveInt("-1")).toEqual(Result.fail("not a positive integer: -1"))
  })
})

describe("Effect and Layer", () => {
  const greetWith = (message: string) =>
    Effect.runPromise(greet("Zettai").pipe(Effect.provide(Layer.succeed(Greeting, { message }))))

  it("uses the greeting provided by the layer", async () => {
    expect(await greetWith("Hello")).toBe("Hello, Zettai")
  })

  it("changes the result when the layer is replaced", async () => {
    expect(await greetWith("Bonjour")).toBe("Bonjour, Zettai")
  })
})
