import { describe, expect, it } from "vitest"
import { canTransitionTo } from "../src/domain/status-transition.ts"
import type { ToDoStatus } from "../src/domain/todo-list.ts"

type Pair = readonly [ToDoStatus, ToDoStatus]

// 状態を足すと、ここが型エラーになります。足した後は、下の一覧に無い組でテストが失敗します。
const everyStatus: Record<ToDoStatus, true> = {
  Todo: true,
  InProgress: true,
  Done: true,
  Blocked: true
}

// 期待は、ドメインモデル設計の表から書きます。実装の表を写しません。
const allowed: ReadonlyArray<Pair> = [
  ["Todo", "InProgress"],
  ["Todo", "Done"],
  ["InProgress", "Done"],
  ["InProgress", "Blocked"],
  ["Blocked", "InProgress"]
]

const rejected: ReadonlyArray<Pair> = [
  ["Todo", "Todo"],
  ["Todo", "Blocked"],
  ["InProgress", "Todo"],
  ["InProgress", "InProgress"],
  ["Done", "Todo"],
  ["Done", "InProgress"],
  ["Done", "Done"],
  ["Done", "Blocked"],
  ["Blocked", "Todo"],
  ["Blocked", "Done"],
  ["Blocked", "Blocked"]
]

describe("canTransitionTo", () => {
  it.each(allowed)("allows %s -> %s", (from, to) => {
    expect(canTransitionTo(from, to)).toBe(true)
  })

  it.each(rejected)("rejects %s -> %s", (from, to) => {
    expect(canTransitionTo(from, to)).toBe(false)
  })

  // 片方の一覧に書き忘れがあると、確かめていない組が残ります。
  it("is checked for every pair of statuses", () => {
    const names = Object.keys(everyStatus)
    const expected = names.flatMap((from) => names.map((to) => `${from}>${to}`))

    const checked = [...allowed, ...rejected].map(([from, to]) => `${from}>${to}`)

    expect(checked.toSorted()).toEqual(expected.toSorted())
  })
})
