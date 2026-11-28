import { Result } from "effect"
import fc from "fast-check"
import { describe, expect, it } from "vitest"
import { parseListName, validateNewListName, validateRename } from "../src/domain/todo-list.ts"
import { zipWith } from "../src/domain/validation.ts"
import type { Validation } from "../src/domain/validation.ts"

const TRIALS = 500

const long = "a".repeat(41)
const problemsOf = (value: Validation<unknown, unknown>) =>
  Result.isFailure(value) ? value.failure : []

describe("validateNewListName", () => {
  it.each(["book", "a".repeat(40), "\u{1f95b}".repeat(40)])("accepts %j", (raw) => {
    expect(Result.getOrUndefined(validateNewListName(raw))).toBe(raw)
  })

  it.each([
    ["", ["Blank"]],
    [long, ["TooLong"]],
    ["a\u0000", ["ForbiddenCharacter"]],
    [`${long}\u0000`, ["TooLong", "ForbiddenCharacter"]]
  ])("returns every problem of %j, in order", (raw, problems) => {
    expect(problemsOf(validateNewListName(raw))).toEqual(problems)
  })

  // 長さの制限は、新しい名前の入力だけに掛けます。保存済みの名前は読めます。
  it("does not apply the length limit to names that are already saved", () => {
    expect(Result.isSuccess(parseListName(long))).toBe(true)
  })
})

const validation: fc.Arbitrary<Validation<number, string>> = fc.oneof(
  fc.integer().map((n) => Result.succeed(n)),
  fc
    .array(fc.constantFrom("a", "b", "c"), { minLength: 1, maxLength: 3 })
    .map((e) => Result.fail(e))
)

const pair = <A, B>(a: A, b: B) => [a, b] as const

describe("zipWith", () => {
  it("keeps both values when both succeed", () => {
    fc.assert(
      fc.property(fc.integer(), fc.integer(), (a, b) => {
        expect(zipWith(Result.succeed(a), Result.succeed(b), pair)).toEqual(Result.succeed([a, b]))
      }),
      { numRuns: TRIALS }
    )
  })

  it("collects the problems of both sides, left first", () => {
    fc.assert(
      fc.property(validation, validation, (a, b) => {
        expect(problemsOf(zipWith(a, b, pair))).toEqual([...problemsOf(a), ...problemsOf(b)])
      }),
      { numRuns: TRIALS }
    )
  })

  it("does not depend on how three validations are grouped", () => {
    fc.assert(
      fc.property(validation, validation, validation, (a, b, c) => {
        const leftFirst = zipWith(zipWith(a, b, pair), c, ([x, y], z) => [x, y, z])
        const rightFirst = zipWith(a, zipWith(b, c, pair), (x, [y, z]) => [x, y, z])

        expect(leftFirst).toEqual(rightFirst)
      }),
      { numRuns: TRIALS }
    )
  })

  // 逐次の合成は、後ろの検証を実行しません。
  it("differs from flatMap, which stops at the first failure", () => {
    const left = Result.fail(["a"])
    const right = Result.fail(["b"])

    const sequential = Result.flatMap(left, () => right)

    expect(problemsOf(sequential)).toEqual(["a"])
    expect(problemsOf(zipWith(left, right, pair))).toEqual(["a", "b"])
  })
})

describe("validateRename", () => {
  it("returns the problems of both inputs at once", () => {
    expect(problemsOf(validateRename("", long))).toEqual([
      { field: "user", problem: "Blank" },
      { field: "newName", problem: "TooLong" }
    ])
  })
})
