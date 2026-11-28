import { describe, expect, it } from "vitest"
import { listNameOf, userOf } from "../support/model.ts"
import type { ZettaiDriver } from "./driver.ts"
import { httpRoute } from "./http-route.ts"

// 経路そのものを確かめます。後始末は型に現れないので、テストで固定します。
describe("httpRoute", () => {
  const ask = (zettai: ZettaiDriver) => zettai.itemsOf(userOf("uberto"), listNameOf("book"))

  it("stops the server after the scenario", async () => {
    const leaked = await httpRoute([], async (zettai) => zettai)

    await expect(ask(leaked)).rejects.toThrow("fetch failed")
  })

  it("stops the server even when the scenario fails", async () => {
    const used: Array<ZettaiDriver> = []

    await expect(
      httpRoute([], async (zettai) => {
        used.push(zettai)
        throw new Error("scenario failed")
      })
    ).rejects.toThrow("scenario failed")

    await expect(Promise.all(used.map(ask))).rejects.toThrow("fetch failed")
  })
})
