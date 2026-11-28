import { Result } from "effect"
import { itemAdded, listCreated } from "../domain/events.ts"
import type { ToDoListEvent } from "../domain/events.ts"
import { makeToDoItem, parseListName, parseUser } from "../domain/todo-list.ts"

// ソースに書いた固定の名前です。誤っていれば、起動の時点で失敗します。
const uberto = Result.getOrThrow(parseUser("uberto"))
const book = Result.getOrThrow(parseListName("book"))

export const sampleHistory: ReadonlyArray<ToDoListEvent> = [
  listCreated(uberto, book),
  ...["write chapter", "insert code", "publish book"].map((description) =>
    itemAdded(uberto, book, makeToDoItem(description))
  )
]
