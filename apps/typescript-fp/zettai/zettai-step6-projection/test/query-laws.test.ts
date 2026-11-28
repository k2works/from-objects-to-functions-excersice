import { Effect } from "effect"
import fc from "fast-check"
import { isDeepStrictEqual } from "node:util"
import { describe, expect, it } from "vitest"

const TRIALS = 500

type Query = Effect.Effect<ReadonlyArray<number>>
// 問い合わせの結果の 1 件ずつに、関数を適用します。Effect の中の配列という、2 段の入れ物です。
type QueryMap = (self: Query, f: (n: number) => number) => Query

const queryMap: QueryMap = (self, f) => self.pipe(Effect.map((rows) => rows.map(f)))
// 最後の 1 件を落とします。
const dropsLast: QueryMap = (self, f) => self.pipe(Effect.map((rows) => rows.slice(0, -1).map(f)))
// 最初の 1 件にだけ、関数を適用します。
const onlyFirst: QueryMap = (self, f) =>
  self.pipe(Effect.map((rows) => rows.map((row, index) => (index === 0 ? f(row) : row))))

const fn = fc.constantFrom(
  { name: "add 1", run: (n: number) => n + 1 },
  { name: "double", run: (n: number) => n * 2 }
)
const rows = fc.array(fc.integer({ min: -100, max: 100 }), { maxLength: 6 })

const observe = async (values: ReadonlyArray<number>, build: (source: Query) => Query) => {
  const log: Array<string> = []
  const source: Query = Effect.sync(() => {
    log.push("query")
    return values
  })
  return { result: await Effect.runPromise(build(source)), log }
}

const identity = (map: QueryMap) =>
  fc.asyncProperty(rows, async (values) =>
    isDeepStrictEqual(
      await observe(values, (source) => map(source, (n) => n)),
      await observe(values, (source) => source)
    )
  )

const composition = (map: QueryMap) =>
  fc.asyncProperty(rows, fn, fn, async (values, f, g) =>
    isDeepStrictEqual(
      await observe(values, (source) => map(map(source, f.run), g.run)),
      await observe(values, (source) => map(source, (n) => g.run(f.run(n))))
    )
  )

const holds = async (property: fc.IAsyncProperty<unknown>) =>
  !(await fc.check(property, { numRuns: TRIALS })).failed

describe("mapping the rows of a query", () => {
  it("keeps the identity and composition laws", async () => {
    await fc.assert(identity(queryMap), { numRuns: TRIALS })
    await fc.assert(composition(queryMap), { numRuns: TRIALS })
  })

  it("can be told from a map that drops a row by identity", async () => {
    expect(await holds(identity(dropsLast))).toBe(false)
  })

  // 一部の要素にだけ関数を適用しても、2 つの法則は成り立ちます。
  it("cannot be told by these laws from a map that changes only the first row", async () => {
    expect(await holds(identity(onlyFirst))).toBe(true)
    expect(await holds(composition(onlyFirst))).toBe(true)
    expect(await Effect.runPromise(onlyFirst(Effect.succeed([1, 2]), (n) => n + 1))).toEqual([2, 2])
  })
})
