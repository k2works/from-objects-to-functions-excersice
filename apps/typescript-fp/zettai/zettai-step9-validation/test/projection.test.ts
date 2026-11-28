import fc from "fast-check"
import { describe, expect, it } from "vitest"
import { itemStatusChanged, listCreated } from "../src/domain/events.ts"
import type { ToDoListEvent } from "../src/domain/events.ts"
import { projectRows } from "../src/domain/projection.ts"
import type { ToDoListRow } from "../src/domain/projection.ts"
import { replay } from "../src/domain/replay.ts"
import type { User } from "../src/domain/todo-list.ts"
import { busyEvents } from "./support/arbitraries.ts"
import { createdWith } from "./support/history.ts"
import { listNameOf, userOf } from "./support/model.ts"

const TRIALS = 500

const uberto = userOf("uberto")
const row = (listName: string, itemCount: number, doneCount: number): ToDoListRow => ({
  listName: listNameOf(listName),
  itemCount,
  doneCount
})

describe("projectRows", () => {
  it("counts the items and the done items of each list, in the order of creation", () => {
    const history = [
      ...createdWith("uberto", "shopping", "milk", "eggs"),
      ...createdWith("uberto", "book"),
      itemStatusChanged(uberto, listNameOf("shopping"), "eggs", "Done")
    ]

    expect(projectRows(uberto, history)).toEqual([row("shopping", 2, 1), row("book", 0, 0)])
  })

  it("does not show the lists of another user", () => {
    const history = [...createdWith("frank", "gardening", "water"), ...createdWith("uberto", "a")]

    expect(projectRows(uberto, history)).toEqual([row("a", 0, 0)])
  })

  it("has no rows for a user without lists", () => {
    expect(projectRows(userOf("somebody"), createdWith("uberto", "book"))).toEqual([])
  })

  it("keeps the position of a list that is created again", () => {
    const history = [
      ...createdWith("uberto", "a", "x"),
      ...createdWith("uberto", "b"),
      listCreated(uberto, listNameOf("a"))
    ]

    expect(projectRows(uberto, history)).toEqual([row("a", 0, 0), row("b", 0, 0)])
  })
})

// 同じ出来事の列から、状態を作ってから数えた行です。射影とは別の道筋です。
const rowsFromState = (user: User, history: ReadonlyArray<ToDoListEvent>) =>
  [...(replay(history).get(user)?.values() ?? [])].map((list) => ({
    listName: list.listName,
    itemCount: list.items.length,
    doneCount: list.items.filter((item) => item.status === "Done").length
  }))

describe("the projection and the state", () => {
  it("agree for every generated history", () => {
    fc.assert(
      fc.property(busyEvents, (history) => {
        expect(projectRows(uberto, history)).toEqual(rowsFromState(uberto, history))
      }),
      { numRuns: TRIALS }
    )
  })

  // 完了が数えられない列ばかりなら、上の性質は完了数を確かめていません。
  it("is compared on histories that often have done items", () => {
    const samples = fc.sample(busyEvents, { numRuns: TRIALS, seed: 1 })

    const withDone = samples.filter((history) =>
      projectRows(uberto, history).some((each) => each.doneCount > 0)
    )

    expect(withDone.length).toBeGreaterThan(TRIALS / 5)
  })
})
