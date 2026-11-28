import { afterAll, beforeAll, describe, it } from "vitest"
import { createdWith } from "../support/history.ts"
import { listOwner } from "./actor.ts"
import { startRouteDatabase, stopRouteDatabase } from "./database-route.ts"
import { routes } from "./routes.ts"

// 準備は、起きた出来事の列で書きます。
const history = [
  ...createdWith("uberto", "shopping", "milk & <bread>", "eggs"),
  ...createdWith("uberto", "empty"),
  ...createdWith("frank", "gardening", "water the plants")
]

beforeAll(startRouteDatabase, 120_000)
afterAll(stopRouteDatabase)

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

  it("shows the items added to a newly created list in order", () =>
    route(history, async (zettai) => {
      const uberto = listOwner(zettai, "uberto")

      await uberto.creates("trip")
      await uberto.adds("trip", "passport")
      await uberto.adds("trip", "tickets")

      await uberto.canSee("trip", ["passport", "tickets"])
    }))

  it("shows the owner all the lists with their numbers of items and done items", () =>
    route(history, async (zettai) => {
      const uberto = listOwner(zettai, "uberto")

      await uberto.creates("<trip>")
      await uberto.adds("<trip>", "passport")
      await uberto.adds("<trip>", "tickets")
      await uberto.finishes("<trip>", "passport")

      await uberto.seesLists([
        ["shopping", 2, 0],
        ["empty", 0, 0],
        ["<trip>", 2, 1]
      ])
    }))

  it("lets the owner rename a list, keeping its items", () =>
    route(history, async (zettai) => {
      const uberto = listOwner(zettai, "uberto")

      await uberto.renames("shopping", "<weekend> shop")

      await uberto.canSee("<weekend> shop", ["milk & <bread>", "eggs"])
      await uberto.cannotSee("shopping")
    }))

  it("tells the owner every problem of a new name at once", () =>
    route(history, async (zettai) => {
      const uberto = listOwner(zettai, "uberto")

      await uberto.cannotRename("shopping", `${"a".repeat(41)}\u0000`, [
        "TooLong",
        "ForbiddenCharacter"
      ])

      await uberto.canSee("shopping", ["milk & <bread>", "eggs"])
    }))

  it("shows no lists to a user who has none", () =>
    route(history, async (zettai) => {
      await listOwner(zettai, "somebody").seesLists([])
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
