import { readdirSync, readFileSync } from "node:fs"
import { describe, expect, it } from "vitest"

const domain = new URL("../src/domain/", import.meta.url)
const IMPORT = /(?:\bfrom|\bimport)\s*\(?\s*["']([^"']+)["']/g

// domain が使ってよいのは、effect の本体と、domain の中のファイルだけです。
const isAllowed = (specifier: string): boolean =>
  specifier === "effect" || /^\.\/[\w-]+\.ts$/.test(specifier)

describe("domain boundary", () => {
  const files = readdirSync(domain, { recursive: true, encoding: "utf-8" }).filter((file) =>
    file.endsWith(".ts")
  )

  it("has files to check", () => {
    expect(files.length).toBeGreaterThan(0)
  })

  it.each(files)("%s imports only effect and the domain", (file) => {
    const source = readFileSync(new URL(file, domain), "utf-8")
    const imported = [...source.matchAll(IMPORT)].map((match) => match[1] ?? "")

    expect(imported.filter((specifier) => !isAllowed(specifier))).toEqual([])
  })
})

// domain の経路が web を通ると、2 つの経路が同じ部分を通り、分けた意味が薄れます。
describe("domain route", () => {
  it("does not go through the web", () => {
    const source = readFileSync(new URL("./acceptance/domain-route.ts", import.meta.url), "utf-8")

    expect([...source.matchAll(IMPORT)].filter((match) => match[1]?.includes("/web/"))).toEqual([])
  })
})

// web が使ってよい domain のファイルは、ハブと型だけです。取得の依存は、ハブの内側にあります。
const ALLOWED_FOR_WEB = ["../domain/errors.ts", "../domain/hub.ts", "../domain/todo-list.ts"]

describe("web boundary", () => {
  const web = new URL("../src/web/", import.meta.url)
  const files = readdirSync(web, { encoding: "utf-8" }).filter((file) => file.endsWith(".ts"))

  it("has files to check", () => {
    expect(files.length).toBeGreaterThan(0)
  })

  it.each(files)("%s reaches the domain only through the hub and the types", (file) => {
    const source = readFileSync(new URL(file, web), "utf-8")
    const imported = [...source.matchAll(IMPORT)]
      .map((match) => match[1] ?? "")
      .filter((specifier) => specifier.includes("domain/"))

    expect(imported.filter((specifier) => !ALLOWED_FOR_WEB.includes(specifier))).toEqual([])
  })
})
