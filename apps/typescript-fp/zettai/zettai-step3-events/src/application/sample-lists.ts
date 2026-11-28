import { Result } from "effect"
import type { ToDoListStore } from "../domain/find-todo-list.ts"
import { makeToDoItem, parseListName, parseUser } from "../domain/todo-list.ts"

// ソースに書いた固定の名前です。誤っていれば、起動の時点で失敗します。
const uberto = Result.getOrThrow(parseUser("uberto"))
const book = Result.getOrThrow(parseListName("book"))

export const sampleLists: ToDoListStore = new Map([
  [
    uberto,
    [
      {
        listName: book,
        items: ["write chapter", "insert code", "publish book"].map((description) =>
          makeToDoItem(description)
        )
      }
    ]
  ]
])
