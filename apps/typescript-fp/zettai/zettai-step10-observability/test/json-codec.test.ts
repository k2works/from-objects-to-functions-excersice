import { Result } from "effect"
import fc from "fast-check"
import { isDeepStrictEqual } from "node:util"
import { describe, expect, it } from "vitest"
import { eventCodec } from "../src/adapter/postgres/codec.ts"
import { dimap } from "../src/adapter/json/json-codec.ts"
import type { JsonCodec } from "../src/adapter/json/json-codec.ts"
import { events } from "./support/arbitraries.ts"

const TRIALS = 500

// 数を文字列にして書く、小さな変換です。法則は、この上で確かめます。
const numbers: JsonCodec<number, number> = {
  encode: (value) => String(value),
  decode: (json) =>
    typeof json === "string" ? Result.succeed(Number(json)) : Result.fail("not a string")
}

const fn = fc.constantFrom(
  { name: "add 1", run: (n: number) => n + 1 },
  { name: "double", run: (n: number) => n * 2 }
)
const value = fc.integer({ min: -1000, max: 1000 })
const json = fc.oneof(value.map(String), fc.constant(null))

// 2 つの変換が同じかは、同じ入力に同じ結果を返すかで比べます。
const same = <In, Out>(
  left: JsonCodec<In, Out>,
  right: JsonCodec<In, Out>,
  input: In,
  encoded: unknown
) =>
  isDeepStrictEqual(left.encode(input), right.encode(input)) &&
  isDeepStrictEqual(left.decode(encoded), right.decode(encoded))

const id = (n: number) => n

describe("dimap", () => {
  it("keeps the identity law", () => {
    fc.assert(
      fc.property(value, json, (n, encoded) => same(dimap(numbers, id, id), numbers, n, encoded)),
      { numRuns: TRIALS }
    )
  })

  it("keeps the composition law", () => {
    fc.assert(
      fc.property(fn, fn, fn, fn, value, json, (f, g, h, i, n, encoded) =>
        same(
          dimap(dimap(numbers, f.run, g.run), h.run, i.run),
          // 書く側の関数は逆の順に、読む側の関数は順に合成します。
          dimap(
            numbers,
            (x: number) => f.run(h.run(x)),
            (x: number) => i.run(g.run(x))
          ),
          n,
          encoded
        )
      ),
      { numRuns: TRIALS }
    )
  })

  // 法則を満たしても、書いて読むと元に戻るとは限りません。
  it("does not promise a round trip", () => {
    const lopsided = dimap(numbers, (n: number) => n + 1, id)

    expect(lopsided.decode(lopsided.encode(1))).toEqual(Result.succeed(2))
  })
})

describe("the codec of stored events", () => {
  it("reads back every generated event as it was written", () => {
    fc.assert(
      fc.property(events, (history) => {
        for (const event of history) {
          const row = eventCodec.encode(event)
          // 保存するときと同じく、JSON の文字列を経由します。
          const stored = JSON.parse(JSON.stringify(row)) as unknown

          expect(eventCodec.decode(stored)).toEqual(Result.succeed(event))
        }
      }),
      { numRuns: TRIALS }
    )
  })

  it.each([null, 1, {}, { event_type: 1, payload: {} }])("refuses %j", (row) => {
    expect(Result.isFailure(eventCodec.decode(row))).toBe(true)
  })

  it("covers every kind of event with the generator", () => {
    const kinds = fc
      .sample(events, { numRuns: TRIALS, seed: 1 })
      .flatMap((history) => history.map((event) => event._tag))

    expect([...new Set(kinds)].sort()).toEqual([
      "ItemAdded",
      "ItemStatusChanged",
      "ListCreated",
      "ListRenamed"
    ])
  })

  // 期限や状態を持つ項目が生成されないと、往復の性質はそれらを確かめません。
  it("generates items with every status, with and without a due date", () => {
    const items = fc
      .sample(events, { numRuns: TRIALS, seed: 1 })
      .flatMap((history) => history.flatMap((e) => (e._tag === "ItemAdded" ? [e.item] : [])))

    expect([...new Set(items.map((item) => item.status))].sort()).toEqual([
      "Blocked",
      "Done",
      "InProgress",
      "Todo"
    ])
    expect(new Set(items.map((item) => item.dueDate._tag))).toEqual(new Set(["None", "Some"]))
  })
})
