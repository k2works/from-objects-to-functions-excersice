import { Option } from "effect"
import { describe, expect, it } from "vitest"
import { itemStatusChanged } from "../src/domain/events.ts"
import { replay } from "../src/domain/replay.ts"
import { apply, listIn } from "../src/domain/state.ts"
import type { ToDoListState } from "../src/domain/state.ts"
import { createdWith } from "./support/history.ts"
import { listNameOf, userOf } from "./support/model.ts"

const uberto = userOf("uberto")
const book = listNameOf("book")
const written = replay(createdWith("uberto", "book", "write", "publish", "write"))

const statusesIn = (state: ToDoListState) =>
  listIn(state, uberto, book).pipe(
    Option.map((list) => list.items.map((item) => `${item.description}:${item.status}`))
  )

describe("apply ItemStatusChanged", () => {
  it("changes only the first item of the description, in a new state", () => {
    const changed = apply(written, itemStatusChanged(uberto, book, "write", "InProgress"))

    expect(statusesIn(changed)).toEqual(
      Option.some(["write:InProgress", "publish:Todo", "write:Todo"])
    )
    expect(statusesIn(written)).toEqual(Option.some(["write:Todo", "publish:Todo", "write:Todo"]))
  })

  it("ignores a change for an item or a list that does not exist", () => {
    expect(apply(written, itemStatusChanged(uberto, book, "sleep", "Done"))).toBe(written)
    expect(apply(written, itemStatusChanged(uberto, listNameOf("music"), "write", "Done"))).toBe(
      written
    )
    expect(apply(written, itemStatusChanged(userOf("frank"), book, "write", "Done"))).toBe(written)
  })
})
