import { Result } from "effect"
import { describe, expect, it } from "vitest"
import { parsePort } from "../src/application/port.ts"

describe("parsePort", () => {
  it("uses 8080 when nothing is specified", () => {
    expect(parsePort(undefined)).toEqual(Result.succeed(8080))
  })

  it("accepts a port number", () => {
    expect(parsePort("18123")).toEqual(Result.succeed(18123))
  })

  it.each(["", "abc", "0", "65536", "80.5", " 80", "-1"])("rejects %j", (raw) => {
    expect(parsePort(raw)).toEqual(Result.fail(`ポート番号が正しくありません: ${raw}`))
  })
})
