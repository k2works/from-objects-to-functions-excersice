import { Result } from "effect"
import fc from "fast-check"
import { describe, expect, it } from "vitest"
import { addToDoItem, changeItemStatus, createToDoList } from "../src/domain/commands.ts"
import type { ToDoListCommand } from "../src/domain/commands.ts"
import { itemAdded, itemStatusChanged, listCreated } from "../src/domain/events.ts"
import { handle } from "../src/domain/handle.ts"
import { replay } from "../src/domain/replay.ts"
import { apply, emptyState } from "../src/domain/state.ts"
import type { ToDoListState } from "../src/domain/state.ts"
import { makeToDoItem } from "../src/domain/todo-list.ts"
import { commands } from "./support/arbitraries.ts"
import { createdWith } from "./support/history.ts"
import { listNameOf, userOf } from "./support/model.ts"

const TRIALS = 500

const uberto = userOf("uberto")
const book = listNameOf("book")
const item = makeToDoItem("write")
const written = replay(createdWith("uberto", "book", "write"))

describe("handle CreateToDoList", () => {
  it("creates a list that does not exist", () => {
    expect(handle(createToDoList(uberto, book), emptyState)).toEqual(
      Result.succeed([listCreated(uberto, book)])
    )
  })

  it("rejects a list that already exists", () => {
    expect(handle(createToDoList(uberto, book), written)).toEqual(
      Result.fail({ _tag: "ListAlreadyExists", user: "uberto", listName: "book" })
    )
  })
})

describe("handle AddToDoItem", () => {
  it("adds an item to an existing list", () => {
    expect(handle(addToDoItem(uberto, book, item), written)).toEqual(
      Result.succeed([itemAdded(uberto, book, item)])
    )
  })

  it("rejects a list that does not exist", () => {
    expect(handle(addToDoItem(uberto, book, item), emptyState)).toEqual(
      Result.fail({ _tag: "ListNotFound", user: "uberto", listName: "book" })
    )
  })
})

describe("handle ChangeItemStatus", () => {
  const change = (description: string, to: "InProgress" | "Blocked") =>
    changeItemStatus(uberto, book, description, to)

  it("changes the status along an allowed transition", () => {
    expect(handle(change("write", "InProgress"), written)).toEqual(
      Result.succeed([itemStatusChanged(uberto, book, "write", "InProgress")])
    )
  })

  it("rejects a list that does not exist", () => {
    expect(handle(change("write", "InProgress"), emptyState)).toEqual(
      Result.fail({ _tag: "ListNotFound", user: "uberto", listName: "book" })
    )
  })

  it("rejects an item that does not exist", () => {
    expect(handle(change("sleep", "InProgress"), written)).toEqual(
      Result.fail({ _tag: "ItemNotFound", user: "uberto", listName: "book", description: "sleep" })
    )
  })

  it("rejects a transition that is not allowed, with both statuses", () => {
    expect(handle(change("write", "Blocked"), written)).toEqual(
      Result.fail({ _tag: "InvalidTransition", description: "write", from: "Todo", to: "Blocked" })
    )
  })
})

// コマンドを順に処理して、受け付けた出来事だけを適用します。
const run = (
  history: ReadonlyArray<ToDoListCommand>,
  seen: (command: ToDoListCommand, outcome: string) => void = () => {}
): void => {
  let state: ToDoListState = emptyState
  for (const command of history) {
    const decided = handle(command, state)
    if (Result.isFailure(decided)) {
      seen(command, decided.failure._tag)
      continue
    }
    seen(command, "accepted")
    for (const event of decided.success) {
      const next = apply(state, event)
      // apply は、対象の無い出来事を黙って無視します。handle が出す出来事は、必ず効きます。
      expect(next).not.toEqual(state)
      state = next
    }
  }
}

describe("events decided by handle", () => {
  it("always change the state when applied", () => {
    fc.assert(
      fc.property(commands, (history) => {
        run(history)
      }),
      { numRuns: TRIALS }
    )
  })
})

// 拒否ばかりの列では、上の性質は何も確かめずに通ります。
describe("generated commands", () => {
  it("are accepted and rejected for every kind and every reason", () => {
    const counts = new Map<string, number>()
    for (const history of fc.sample(commands, { numRuns: TRIALS, seed: 1 })) {
      run(history, (command, outcome) => {
        const key = `${command._tag} ${outcome}`
        counts.set(key, (counts.get(key) ?? 0) + 1)
      })
    }

    expect([...counts.keys()].sort()).toEqual([
      "AddToDoItem ListNotFound",
      "AddToDoItem accepted",
      "ChangeItemStatus InvalidTransition",
      "ChangeItemStatus ItemNotFound",
      "ChangeItemStatus ListNotFound",
      "ChangeItemStatus accepted",
      "CreateToDoList ListAlreadyExists",
      "CreateToDoList accepted",
      "RenameToDoList ListAlreadyExists",
      "RenameToDoList ListNotFound",
      "RenameToDoList accepted"
    ])
    expect(counts.get("ChangeItemStatus accepted")).toBeGreaterThan(50)
  })
})
