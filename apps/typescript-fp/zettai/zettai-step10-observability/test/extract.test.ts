import { Result } from "effect"
import { expect, it } from "vitest"
import { extractRequest } from "../src/web/app.ts"
import { listNameOf, userOf } from "./support/model.ts"

// 矢印 1 だけを呼びます。応答の 404 からは分からない、取り出した値を比べます。
it("takes the user and the list name from the path in this order", () => {
  const request = new Request("http://localhost/todo/uberto/book")

  expect(extractRequest(request)).toEqual(
    Result.succeed({ _tag: "ShowList", user: userOf("uberto"), listName: listNameOf("book") })
  )
})

it("takes only the user from the path of the lists page", () => {
  const request = new Request("http://localhost/todo/uberto")

  expect(extractRequest(request)).toEqual(
    Result.succeed({ _tag: "ShowLists", user: userOf("uberto") })
  )
})
