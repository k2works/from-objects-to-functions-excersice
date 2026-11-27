import { Option } from "effect"
import { describe, expect, it } from "vitest"
import { findToDoList } from "../src/domain/find-todo-list.ts"
import type { ToDoList } from "../src/domain/todo-list.ts"

const book: ToDoList = { listName: { name: "book" }, items: [{ description: "write chapter" }] }
const fetch = findToDoList(new Map([["uberto", [book]]]))

describe("findToDoList", () => {
  it("finds a list by the names, not by the object identity", () => {
    expect(fetch({ name: "uberto" }, { name: "book" })).toEqual(Option.some(book))
  })

  it("returns none for an unknown list", () => {
    expect(fetch({ name: "uberto" }, { name: "music" })).toEqual(Option.none())
  })

  it("returns none for an unknown user", () => {
    expect(fetch({ name: "somebody" }, { name: "book" })).toEqual(Option.none())
  })
})
