import { Effect, Result } from "effect"
import fc from "fast-check"
import { isDeepStrictEqual } from "node:util"
import { describe, expect, it } from "vitest"

const TRIALS = 500

type Log = Array<string>
// 実行すると、名前を記録してから、結果を返す Effect を作ります。
type Step = (log: Log) => (n: number) => Effect.Effect<number, string>

const logged =
  (name: string, run: (n: number) => Result.Result<number, string>): Step =>
  (log) =>
  (n) =>
    Effect.suspend(() => {
      log.push(`${name} ${n}`)
      return Effect.fromResult(run(n))
    })

// 失敗する処理も含めます。失敗の後ろは、実行されてはいけません。
const step = fc.constantFrom(
  { name: "add 1", of: logged("add 1", (n) => Result.succeed(n + 1)) },
  { name: "double", of: logged("double", (n) => Result.succeed(n * 2)) },
  {
    name: "reject negative",
    of: logged("reject negative", (n) => (n < 0 ? Result.fail("negative") : Result.succeed(n)))
  }
)
const value = fc.integer({ min: -100, max: 100 })

type Bind = <E>(
  self: Effect.Effect<number, E>,
  f: (n: number) => Effect.Effect<number, string>
) => Effect.Effect<number, E | string>

const flatMap: Bind = (self, f) => Effect.flatMap(self, f)
// 続きの処理を 2 回実行します。返す値は、2 回目の結果です。
const runsTwice: Bind = (self, f) => Effect.flatMap(self, (n) => Effect.flatMap(f(n), () => f(n)))

const observe = async (build: (log: Log) => Effect.Effect<number, string>) => {
  const log: Log = []
  const exit = await Effect.runPromiseExit(build(log))
  return { exit, log }
}

type Observed = Awaited<ReturnType<typeof observe>>
type Same = (left: Observed, right: Observed) => boolean
type Law = (bind: Bind, same: Same) => fc.IAsyncProperty<unknown>
const sameExitAndLog: Same = (left, right) => isDeepStrictEqual(left, right)
const sameExit: Same = (left, right) => isDeepStrictEqual(left.exit, right.exit)

// 左単位元: 値を包んでから続けることは、続きを直接呼ぶことと同じです。
const leftIdentity = (bind: Bind, same: Same) =>
  fc.asyncProperty(value, step, async (n, f) =>
    same(
      await observe((log) => bind(Effect.succeed(n), f.of(log))),
      await observe((log) => f.of(log)(n))
    )
  )

// 右単位元: 何もせずに包み直す続きは、足しても変わりません。
const rightIdentity = (bind: Bind, same: Same) =>
  fc.asyncProperty(value, step, async (n, f) =>
    same(
      await observe((log) => bind(f.of(log)(n), (m) => Effect.succeed(m))),
      await observe((log) => f.of(log)(n))
    )
  )

// 結合律: つなぐ順にまとめても、同じ順に実行され、同じ結果になります。
const associativity = (bind: Bind, same: Same) =>
  fc.asyncProperty(value, step, step, step, async (n, f, g, h) =>
    same(
      await observe((log) => bind(bind(f.of(log)(n), g.of(log)), h.of(log))),
      await observe((log) => bind(f.of(log)(n), (m) => bind(g.of(log)(m), h.of(log))))
    )
  )

const holds = async (property: fc.IAsyncProperty<unknown>) =>
  !(await fc.check(property, { numRuns: TRIALS })).failed

describe("Effect.flatMap", () => {
  it.each<[string, Law]>([
    ["left identity", leftIdentity],
    ["right identity", rightIdentity],
    ["associativity", associativity]
  ])("keeps the %s law", async (_name, law) => {
    await fc.assert(law(flatMap, sameExitAndLog), { numRuns: TRIALS })
  })

  it("can be told from a flatMap that runs the next step twice only with the log", async () => {
    expect(await holds(leftIdentity(runsTwice, sameExit))).toBe(true)
    expect(await holds(leftIdentity(runsTwice, sameExitAndLog))).toBe(false)
  })
})
