import { describe, expect, it } from "vitest"
import { renderPage } from "../src/web/html.ts"

describe("renderPage", () => {
  it("shows the list name and its items", () => {
    const html = renderPage({
      listName: { name: "book" },
      items: [{ description: "write chapter" }, { description: "insert code" }]
    })

    expect(html).toContain('<meta charset="utf-8">')
    expect(html).toContain("<h1>book</h1>")
    expect(html).toContain("<li>write chapter</li><li>insert code</li>")
  })

  it("escapes every value taken from the list", () => {
    const html = renderPage({
      listName: { name: `<b> & "q" 'a'` },
      items: [{ description: "<script>alert(1)</script>" }]
    })

    expect(html).toContain("<h1>&lt;b&gt; &amp; &quot;q&quot; &#39;a&#39;</h1>")
    expect(html).toContain("<li>&lt;script&gt;alert(1)&lt;/script&gt;</li>")
    expect(html).not.toContain("<script>")
  })
})
