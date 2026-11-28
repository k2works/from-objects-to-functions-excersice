import { Option } from "effect"
import { describe, expect, it } from "vitest"
import { findToDoList } from "../src/domain/find-todo-list.ts"
import { listNameOf, listOf, userOf } from "./support/model.ts"

const book = listOf("book", "write chapter")
const fetch = findToDoList(new Map([[userOf("uberto"), [book]]]))

describe("findToDoList", () => {
  it("finds a list by the names, not by object identity", () => {
    expect(fetch(userOf("uberto"), listNameOf("book"))).toEqual(Option.some(book))
  })

  it("returns none for an unknown list", () => {
    expect(fetch(userOf("uberto"), listNameOf("music"))).toEqual(Option.none())
  })

  it("returns none for an unknown user", () => {
    expect(fetch(userOf("somebody"), listNameOf("book"))).toEqual(Option.none())
  })
})
