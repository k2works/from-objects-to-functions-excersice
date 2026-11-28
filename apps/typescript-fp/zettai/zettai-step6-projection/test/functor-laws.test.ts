import { Effect, Result } from "effect"
import fc from "fast-check"
import { isDeepStrictEqual } from "node:util"
import { describe, expect, it } from "vitest"

const TRIALS = 500

interface Fn {
  readonly name: string
  readonly run: (n: number) => number
}

// 反例が読めるように、名前を持つ関数から選びます。順序で結果が変わる組を含めます。
const fn = fc.constantFrom<Fn>(
  { name: "add 1", run: (n) => n + 1 },
  { name: "double", run: (n) => n * 2 },
  { name: "negate", run: (n) => -n }
)

const outcome: fc.Arbitrary<Result.Result<number, string>> = fc.oneof(
  fc.integer({ min: -1000, max: 1000 }).map((n) => Result.succeed(n)),
  fc.constantFrom("not found", "taken").map((reason) => Result.fail(reason))
)

// 法則は、map の実装を受け取る形にします。壊した map にも、同じ法則を当てられます。
type ResultMap = (
  self: Result.Result<number, string>,
  f: (n: number) => number
) => Result.Result<number, string>

const resultIdentity = (map: ResultMap) =>
  fc.property(outcome, (value) =>
    isDeepStrictEqual(
      map(value, (n) => n),
      value
    )
  )

const resultComposition = (map: ResultMap) =>
  fc.property(outcome, fn, fn, (value, f, g) =>
    isDeepStrictEqual(
      map(map(value, f.run), g.run),
      map(value, (n) => g.run(f.run(n)))
    )
  )

const resultMap: ResultMap = (self, f) => Result.map(self, f)
// 成功の値に、関数を 2 回当てます。
const mapsTwice: ResultMap = (self, f) => Result.map(Result.map(self, f), f)
// 失敗を、成功の 0 にします。
const forgetsFailure: ResultMap = (self, f) => Result.succeed(f(Result.getOrElse(self, () => 0)))
// 渡された関数を使いません。
const ignoresFunction: ResultMap = (self) => self

const holds = (property: fc.IProperty<unknown>) => !fc.check(property, { numRuns: TRIALS }).failed

describe("Result.map", () => {
  it("keeps the identity law", () => {
    fc.assert(resultIdentity(resultMap), { numRuns: TRIALS })
  })

  it("keeps the composition law", () => {
    fc.assert(resultComposition(resultMap), { numRuns: TRIALS })
  })

  it("can be told from a map that applies the function twice only by composition", () => {
    expect(holds(resultIdentity(mapsTwice))).toBe(true)
    expect(holds(resultComposition(mapsTwice))).toBe(false)
  })

  it("can be told from a map that forgets the failure by identity", () => {
    expect(holds(resultIdentity(forgetsFailure))).toBe(false)
  })

  // number から number の map だけを試すと、関数を無視する実装も、両方の法則を満たします。
  it("cannot be told by these laws from a map that ignores the function", () => {
    expect(holds(resultIdentity(ignoresFunction))).toBe(true)
    expect(holds(resultComposition(ignoresFunction))).toBe(true)
    expect(ignoresFunction(Result.succeed(1), (n) => n + 1)).toEqual(Result.succeed(1))
  })
})

type Source = Effect.Effect<number, string>
type EffectMap = (self: Source, f: (n: number) => number) => Source

// Effect は値のまま比べられません。実行して、結果と、何が実行されたかの記録を比べます。
const observe = async (value: Result.Result<number, string>, build: (source: Source) => Source) => {
  const log: Array<string> = []
  const source: Source = Effect.suspend(() => {
    log.push("source")
    return Effect.fromResult(value)
  })
  return { exit: await Effect.runPromiseExit(build(source)), log }
}

type Observed = Awaited<ReturnType<typeof observe>>
type Same = (left: Observed, right: Observed) => boolean

const sameExitAndLog: Same = (left, right) => isDeepStrictEqual(left, right)
const sameExit: Same = (left, right) => isDeepStrictEqual(left.exit, right.exit)

const effectIdentity = (map: EffectMap, same: Same) =>
  fc.asyncProperty(outcome, async (value) =>
    same(
      await observe(value, (source) => map(source, (n) => n)),
      await observe(value, (source) => source)
    )
  )

const effectComposition = (map: EffectMap, same: Same) =>
  fc.asyncProperty(outcome, fn, fn, async (value, f, g) =>
    same(
      await observe(value, (source) => map(map(source, f.run), g.run)),
      await observe(value, (source) => map(source, (n) => g.run(f.run(n))))
    )
  )

const effectMap: EffectMap = (self, f) => Effect.map(self, f)
// 元の Effect を 2 回実行します。返す値は正しいままです。
const runsTwice: EffectMap = (self, f) =>
  self.pipe(
    Effect.flatMap(() => self),
    Effect.map(f)
  )

const holdsAsync = async (property: fc.IAsyncProperty<unknown>) =>
  !(await fc.check(property, { numRuns: TRIALS })).failed

describe("Effect.map", () => {
  it("keeps the identity law", async () => {
    await fc.assert(effectIdentity(effectMap, sameExitAndLog), { numRuns: TRIALS })
  })

  it("keeps the composition law", async () => {
    await fc.assert(effectComposition(effectMap, sameExitAndLog), { numRuns: TRIALS })
  })

  it("can be told from a map that runs the source twice only with the log", async () => {
    expect(await holdsAsync(effectIdentity(runsTwice, sameExit))).toBe(true)
    expect(await holdsAsync(effectIdentity(runsTwice, sameExitAndLog))).toBe(false)
  })
})
