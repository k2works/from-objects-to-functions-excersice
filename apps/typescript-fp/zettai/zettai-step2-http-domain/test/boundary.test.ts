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
