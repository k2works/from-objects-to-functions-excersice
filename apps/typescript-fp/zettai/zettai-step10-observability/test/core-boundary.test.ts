import { readFileSync } from "node:fs"
import { describe, expect, it } from "vitest"

// 核は、出来事、コマンド、状態、状態遷移、コマンドの処理です。ハブと web は含みません。
const CORE = [
  "commands.ts",
  "errors.ts",
  "events.ts",
  "handle.ts",
  "projection.ts",
  "replay.ts",
  "state.ts",
  "streams.ts",
  "status-transition.ts",
  "todo-list.ts",
  "transition.ts",
  "validation.ts"
]

const IMPORT = /\bimport\s+(?:type\s+)?([^"']*?)\s*from\s*["']([^"']+)["']/g

const importsOf = (file: string) => {
  const source = readFileSync(new URL(`../src/domain/${file}`, import.meta.url), "utf-8")
  return [...source.matchAll(IMPORT)].map((match) => ({
    names: match[1] ?? "",
    from: match[2] ?? ""
  }))
}

// 核は、実行を表す Effect の型を読み込みません。Result と Option は使います。
describe("pure core", () => {
  it.each(CORE)("%s does not import the Effect type", (file) => {
    const offending = importsOf(file).filter(
      ({ names, from }) =>
        from.startsWith("effect") && (/\bEffect\b|\*/.test(names) || from !== "effect")
    )

    expect(offending).toEqual([])
  })

  // 核の外のファイルを読み込むと、そこを通って Effect が入ります。
  it.each(CORE)("%s imports only other files of the core", (file) => {
    const outside = importsOf(file)
      .map(({ from }) => from)
      .filter((from) => from !== "effect" && !CORE.includes(from.replace("./", "")))

    expect(outside).toEqual([])
  })
})
