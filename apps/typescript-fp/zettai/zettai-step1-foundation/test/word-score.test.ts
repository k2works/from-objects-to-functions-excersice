import fc from "fast-check"
import { describe, expect, it } from "vitest"
import { wordScore } from "../src/word-score.ts"

const TRIALS = 200

describe("wordScore", () => {
  it("scores a word by its length", () => {
    expect(wordScore("TypeScript")).toBe(10)
  })

  it("gives the same score for the same word", () => {
    expect(wordScore("TypeScript")).toBe(wordScore("TypeScript"))
  })

  it("scores two joined words as the sum of their scores", () => {
    fc.assert(
      fc.property(fc.string(), fc.string(), (left, right) => {
        expect(wordScore(left + right)).toBe(wordScore(left) + wordScore(right))
      }),
      { numRuns: TRIALS }
    )
  })
})
