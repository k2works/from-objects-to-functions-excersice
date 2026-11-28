import { Option, Result } from "effect"
import { describe, expect, it } from "vitest"
import { renameToDoList } from "../src/domain/commands.ts"
import type { ToDoListCommand } from "../src/domain/commands.ts"
import { itemStatusChanged, listCreated, listRenamed, itemAdded } from "../src/domain/events.ts"
import type { ToDoListEvent } from "../src/domain/events.ts"
import { handle } from "../src/domain/handle.ts"
import { projectRows } from "../src/domain/projection.ts"
import { replay } from "../src/domain/replay.ts"
import { listIn } from "../src/domain/state.ts"
import { makeToDoItem } from "../src/domain/todo-list.ts"
import { createdWith } from "./support/history.ts"
import { listNameOf, userOf } from "./support/model.ts"

const uberto = userOf("uberto")
const book = listNameOf("book")
const reading = listNameOf("reading")
const history = [
  ...createdWith("uberto", "book", "write", "publish"),
  itemStatusChanged(uberto, book, "write", "Done"),
  ...createdWith("uberto", "music")
]

const decide = (command: ToDoListCommand, past: ReadonlyArray<ToDoListEvent>) =>
  handle(command, replay(past))
const after = (command: ToDoListCommand, past: ReadonlyArray<ToDoListEvent>) => [
  ...past,
  ...Result.getOrThrow(decide(command, past))
]

describe("renaming a list", () => {
  it("ends the old list and writes the current items to the new one", () => {
    expect(decide(renameToDoList(uberto, book, reading), history)).toEqual(
      Result.succeed([
        listRenamed(uberto, book, reading),
        listCreated(uberto, reading),
        itemAdded(uberto, reading, makeToDoItem("write", { status: "Done" })),
        itemAdded(uberto, reading, makeToDoItem("publish"))
      ])
    )
  })

  it("removes the old name from the state and keeps the items and statuses", () => {
    const state = replay(after(renameToDoList(uberto, book, reading), history))

    expect(listIn(state, uberto, book)).toEqual(Option.none())
    expect(Option.getOrThrow(listIn(state, uberto, reading)).items).toEqual(
      Option.getOrThrow(listIn(replay(history), uberto, book)).items
    )
  })

  it("moves the renamed list to the end of the rows", () => {
    const rows = projectRows(uberto, after(renameToDoList(uberto, book, reading), history))

    expect(rows.map((row) => `${row.listName} ${row.itemCount} ${row.doneCount}`)).toEqual([
      "music 0 0",
      "reading 2 1"
    ])
  })

  it("does not duplicate the items when renamed there and back", () => {
    const there = after(renameToDoList(uberto, book, reading), history)
    const back = after(renameToDoList(uberto, reading, book), there)

    expect(listIn(replay(back), uberto, book)).toEqual(listIn(replay(history), uberto, book))
    expect(listIn(replay(back), uberto, reading)).toEqual(Option.none())
  })

  it.each([
    ["a list that does not exist", renameToDoList(uberto, reading, book), "ListNotFound"],
    [
      "a name that is taken",
      renameToDoList(uberto, book, listNameOf("music")),
      "ListAlreadyExists"
    ],
    ["the same name", renameToDoList(uberto, book, book), "ListAlreadyExists"]
  ])("rejects %s", (_name, command, reason) => {
    const decided = decide(command, history)

    expect(Result.isFailure(decided) && decided.failure._tag).toBe(reason)
  })
})
