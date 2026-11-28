import { describe, it } from "vitest"
import { listOf as list, userOf } from "../support/model.ts"
import type { ToDoListStore } from "../../src/domain/find-todo-list.ts"
import { listOwner } from "./actor.ts"
import { routes } from "./routes.ts"

const store: ToDoListStore = new Map([
  [userOf("uberto"), [list("shopping", "milk & <bread>", "eggs"), list("empty")]],
  [userOf("frank"), [list("gardening", "water the plants")]]
])

describe.each(Object.entries(routes))("viewing ToDo lists via %s", (_name, route) => {
  it("shows the owner the items of a list in order", () =>
    route(store, async (zettai) => {
      const uberto = listOwner(zettai, "uberto")

      await uberto.canSee("shopping", ["milk & <bread>", "eggs"])
    }))

  it("shows an empty list as a list without items", () =>
    route(store, async (zettai) => {
      const uberto = listOwner(zettai, "uberto")

      await uberto.canSee("empty", [])
    }))

  it("does not show the lists of another user", () =>
    route(store, async (zettai) => {
      const uberto = listOwner(zettai, "uberto")
      const frank = listOwner(zettai, "frank")

      await frank.canSee("gardening", ["water the plants"])
      await uberto.cannotSee("gardening")
      await frank.cannotSee("shopping")
    }))

  it("does not show a list that does not exist", () =>
    route(store, async (zettai) => {
      const somebody = listOwner(zettai, "somebody")

      await somebody.cannotSee("shopping")
    }))
})
