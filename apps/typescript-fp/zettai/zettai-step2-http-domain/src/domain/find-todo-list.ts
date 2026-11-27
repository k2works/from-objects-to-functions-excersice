import { Option } from "effect"
import type { ListName, ToDoList, User } from "./todo-list.ts"

export type ToDoListFetcher = (user: User, listName: ListName) => Option.Option<ToDoList>

// キーはオブジェクトではなく、利用者の名前の文字列です。Map はオブジェクトのキーを参照で比べます。
export type ToDoListStore = ReadonlyMap<string, ReadonlyArray<ToDoList>>

export const findToDoList =
  (store: ToDoListStore): ToDoListFetcher =>
  (user, listName) =>
    Option.fromUndefinedOr(
      store.get(user.name)?.find((list) => list.listName.name === listName.name)
    )
