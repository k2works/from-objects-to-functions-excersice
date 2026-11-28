import { Option } from "effect"
import { describe, expect, it } from "vitest"
import { itemAdded, listCreated } from "../src/domain/events.ts"
import { apply, emptyState, listIn } from "../src/domain/state.ts"
import type { ToDoListState } from "../src/domain/state.ts"
import { makeToDoItem } from "../src/domain/todo-list.ts"
import { listNameOf, listOf, userOf } from "./support/model.ts"

const uberto = userOf("uberto")
const book = listNameOf("book")
const bookIn = (state: ToDoListState) => listIn(state, uberto, book)

const created = apply(emptyState, listCreated(uberto, book))
const written = apply(created, itemAdded(uberto, book, makeToDoItem("write chapter")))

describe("apply", () => {
  it("creates a list without items", () => {
    expect(bookIn(created)).toEqual(Option.some(listOf("book")))
  })

  it("adds items to the end of the list", () => {
    const published = apply(written, itemAdded(uberto, book, makeToDoItem("publish book")))

    expect(bookIn(published)).toEqual(Option.some(listOf("book", "write chapter", "publish book")))
  })

  it("returns a new state and leaves the previous ones as they were", () => {
    expect(bookIn(emptyState)).toEqual(Option.none())
    expect(bookIn(created)).toEqual(Option.some(listOf("book")))
    expect(bookIn(written)).toEqual(Option.some(listOf("book", "write chapter")))
  })

  it("ignores an item for a list that has not been created", () => {
    const item = makeToDoItem("lost")

    expect(apply(emptyState, itemAdded(uberto, book, item))).toBe(emptyState)
    expect(apply(written, itemAdded(uberto, listNameOf("music"), item))).toBe(written)
    expect(apply(written, itemAdded(userOf("frank"), book, item))).toBe(written)
  })

  it("makes the list empty when it is created again", () => {
    expect(bookIn(apply(written, listCreated(uberto, book)))).toEqual(Option.some(listOf("book")))
  })

  it("keeps the lists of the same name apart by user", () => {
    const both = apply(written, listCreated(userOf("frank"), book))

    expect(bookIn(both)).toEqual(Option.some(listOf("book", "write chapter")))
    expect(listIn(both, userOf("frank"), book)).toEqual(Option.some(listOf("book")))
  })

  // "a/b" + "c" と "a" + "b/c" は、/ でつなぐと同じ文字列になります。
  it("does not mix up names that contain a separator", () => {
    const state = apply(emptyState, listCreated(userOf("a/b"), listNameOf("c")))

    expect(listIn(state, userOf("a"), listNameOf("b/c"))).toEqual(Option.none())
    expect(listIn(state, userOf("a/b"), listNameOf("c"))).toEqual(Option.some(listOf("c")))
  })
})
