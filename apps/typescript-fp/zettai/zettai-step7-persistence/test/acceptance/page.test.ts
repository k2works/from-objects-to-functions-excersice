import { describe, expect, it } from "vitest"
import { renderPage } from "../../src/web/html.ts"
import { listOf } from "../support/model.ts"
import { readListPage } from "./page.ts"

// 受け入れテストが HTML を読み違えると、壊れた画面でも通ります。読み戻し自体を確かめます。
describe("readListPage", () => {
  it("reads back the name and the items rendered by the application", () => {
    const html = renderPage(listOf("<shop>", `a & <b> "c" 'd'`, "&amp;"))

    expect(readListPage(html)).toEqual({ name: "<shop>", items: [`a & <b> "c" 'd'`, "&amp;"] })
  })

  it.each([
    ["a tag", "<h1>x</h1><ul><li><b>bold</b></li></ul>"],
    ["a bare ampersand", "<h1>x</h1><ul><li>a & b</li></ul>"]
  ])("refuses a value that is not escaped: %s", (_case, html) => {
    expect(() => readListPage(html)).toThrow("not escaped")
  })

  it("refuses a page that is not a list page", () => {
    expect(() => readListPage("<html><body>welcome</body></html>")).toThrow("not a list page")
  })
})
