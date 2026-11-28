import { itemAdded, listCreated } from "../../src/domain/events.ts"
import type { ToDoListEvent } from "../../src/domain/events.ts"
import { makeToDoItem } from "../../src/domain/todo-list.ts"
import { listNameOf, userOf } from "./model.ts"

// 「作って、項目を順に足した」という出来事の列を作ります。
export const createdWith = (
  user: string,
  listName: string,
  ...descriptions: ReadonlyArray<string>
): ReadonlyArray<ToDoListEvent> => [
  listCreated(userOf(user), listNameOf(listName)),
  ...descriptions.map((description) =>
    itemAdded(userOf(user), listNameOf(listName), makeToDoItem(description))
  )
]
