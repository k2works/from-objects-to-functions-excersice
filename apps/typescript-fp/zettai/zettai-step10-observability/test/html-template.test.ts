import { describe, expect, it } from "vitest"
import { markup, render } from "../src/web/template.ts"

describe("markup", () => {
  it("escapes every string placed in the template", () => {
    const page = markup`<p title="${`"'`}">${"<script>&"}</p>`

    expect(render(page)).toBe('<p title="&quot;&#39;">&lt;script&gt;&amp;</p>')
  })

  it("does not escape a piece made by the template again", () => {
    const items = ["<a>", "b"].map((item) => markup`<li>${item}</li>`)

    expect(render(markup`<ul>${items}</ul>`)).toBe("<ul><li>&lt;a&gt;</li><li>b</li></ul>")
  })

  it("places numbers as they are", () => {
    expect(render(markup`<td>${3}</td>`)).toBe("<td>3</td>")
  })
})

// 型検査で拒否される書き方です。実行はしません。
export const rejectedValues = (missing: string | undefined) => [
  // @ts-expect-error 値が無いかもしれないものは、置けません
  markup`<p>${missing}</p>`,
  // @ts-expect-error 同じ形のオブジェクトは、検査済みの HTML の代わりになりません
  markup`<p>${{ text: "<script>" }}</p>`
]
