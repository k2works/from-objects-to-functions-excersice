import fc from "fast-check"
import { describe, expect, it } from "vitest"
import { itemAdded, listCreated } from "../src/domain/events.ts"
import type { ToDoListEvent } from "../src/domain/events.ts"
import { replay } from "../src/domain/replay.ts"
import { apply, emptyState } from "../src/domain/state.ts"
import {
  andThen,
  identityTransition,
  transitionOf,
  transitionOfAll
} from "../src/domain/transition.ts"
import type { StateTransition } from "../src/domain/transition.ts"
import { makeToDoItem } from "../src/domain/todo-list.ts"
import { events } from "./support/arbitraries.ts"
import { listNameOf, userOf } from "./support/model.ts"

const TRIALS = 500

type History = ReadonlyArray<ToDoListEvent>

// 関数どうしは比べられません。生成した状態に適用して、結果を比べます。
// 生成するのは、変換ではなく出来事の列です。反例に、原因の出来事が表示されます。
const sameOn = (left: StateTransition, right: StateTransition, history: History) => {
  const state = replay(history)
  expect(left(state)).toEqual(right(state))
}

describe("StateTransition", () => {
  it("is associative under andThen", () => {
    fc.assert(
      fc.property(events, events, events, events, (first, second, third, before) => {
        const a = transitionOfAll(first)
        const b = transitionOfAll(second)
        const c = transitionOfAll(third)
        sameOn(andThen(andThen(a, b), c), andThen(a, andThen(b, c)), before)
      }),
      { numRuns: TRIALS }
    )
  })

  it("has identityTransition as the identity on both sides", () => {
    fc.assert(
      fc.property(events, events, (history, before) => {
        const a = transitionOfAll(history)
        sameOn(andThen(identityTransition, a), a, before)
        sameOn(andThen(a, identityTransition), a, before)
      }),
      { numRuns: TRIALS }
    )
  })

  // 法則は、合成の順序を逆にしても成り立ちます。順序は、畳み込みと比べて固定します。
  it("composes the events in the order they happened", () => {
    fc.assert(
      fc.property(events, (history) => {
        expect(transitionOfAll(history)(emptyState)).toEqual(replay(history))
      }),
      { numRuns: TRIALS }
    )
  })

  it("is not commutative", () => {
    const create = transitionOf(listCreated(userOf("uberto"), listNameOf("book")))
    const add = transitionOf(itemAdded(userOf("uberto"), listNameOf("book"), makeToDoItem("x")))

    expect(andThen(create, add)(emptyState)).not.toEqual(andThen(add, create)(emptyState))
  })
})

// 追加が一度も効かない入力ばかりなら、上の性質は、何もしない変換どうしを比べて通ります。
describe("generated events", () => {
  const addsToAnExistingList = (history: ReadonlyArray<ToDoListEvent>): boolean => {
    let current = emptyState
    return history.some((event) => {
      const next = apply(current, event)
      const effective = event._tag === "ItemAdded" && next !== current
      current = next
      return effective
    })
  }

  it("often add an item to an existing list", () => {
    const samples = fc.sample(events, { numRuns: TRIALS, seed: 1 })

    const effective = samples.filter(addsToAnExistingList).length

    expect(effective).toBeGreaterThan(TRIALS / 5)
  })
})
