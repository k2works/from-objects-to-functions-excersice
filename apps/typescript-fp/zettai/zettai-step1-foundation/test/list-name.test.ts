import { Result } from "effect"
import { describe, expect, it } from "vitest"
import { nonBlankName } from "../src/list-name.ts"

describe("nonBlankName", () => {
  it("keeps a usable name as a success", () => {
    expect(nonBlankName("仕事")).toEqual(Result.succeed("仕事"))
  })

  it("returns a blank name as a failure value, not an exception", () => {
    expect(nonBlankName("  ")).toEqual(Result.fail("名前は空にできません"))
  })
})
