import { Result } from "effect"
import { expect, it } from "vitest"
import { extractListRequest } from "../src/web/app.ts"
import { listNameOf, userOf } from "./support/model.ts"

// 矢印 1 だけを呼びます。応答の 404 からは分からない、取り出した値を比べます。
it("takes the user and the list name from the path in this order", () => {
  const request = new Request("http://localhost/todo/uberto/book")

  expect(extractListRequest(request)).toEqual(
    Result.succeed({ user: userOf("uberto"), listName: listNameOf("book") })
  )
})
