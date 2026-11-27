import { describe, expect, it } from "vitest"
import { drivers } from "./support/drivers.ts"

// 基盤の確認用です。同じシナリオを、経路を差し替えて実行できることだけを確かめます。
describe.each(Object.entries(drivers))("greeting via %s", (_route, driver) => {
  it("greets a visitor by name", async () => {
    expect(await driver.greet("Uberto & friends")).toBe("Hello, Uberto & friends")
  })
})
