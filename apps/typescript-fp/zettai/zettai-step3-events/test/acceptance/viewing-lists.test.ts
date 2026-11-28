import { describe, it } from "vitest"
import { createdWith } from "../support/history.ts"
import { listOwner } from "./actor.ts"
import { routes } from "./routes.ts"

// 準備は、起きた出来事の列で書きます。
const history = [
  ...createdWith("uberto", "shopping", "milk & <bread>", "eggs"),
  ...createdWith("uberto", "empty"),
  ...createdWith("frank", "gardening", "water the plants")
]

describe.each(Object.entries(routes))("viewing ToDo lists via %s", (_name, route) => {
  it("shows the owner the items of a list in order", () =>
    route(history, async (zettai) => {
      const uberto = listOwner(zettai, "uberto")

      await uberto.canSee("shopping", ["milk & <bread>", "eggs"])
    }))

  it("shows an empty list as a list without items", () =>
    route(history, async (zettai) => {
      const uberto = listOwner(zettai, "uberto")

      await uberto.canSee("empty", [])
    }))

  it("shows a list that has just been created as a list without items", () =>
    route([...history, ...createdWith("uberto", "new list")], async (zettai) => {
      const uberto = listOwner(zettai, "uberto")

      await uberto.canSee("new list", [])
    }))

  it("does not show the lists of another user", () =>
    route(history, async (zettai) => {
      const uberto = listOwner(zettai, "uberto")
      const frank = listOwner(zettai, "frank")

      await frank.canSee("gardening", ["water the plants"])
      await uberto.cannotSee("gardening")
      await frank.cannotSee("shopping")
    }))

  it("does not show a list that does not exist", () =>
    route(history, async (zettai) => {
      const somebody = listOwner(zettai, "somebody")

      await somebody.cannotSee("shopping")
    }))
})
