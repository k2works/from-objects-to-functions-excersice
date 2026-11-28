import { Option } from "effect"
import type { ListName, ToDoList, User } from "./todo-list.ts"

export type ToDoListFetcher = (user: User, listName: ListName) => Option.Option<ToDoList>

// User の実行時の値は文字列なので、Map は中身で引けます。
export type ToDoListStore = ReadonlyMap<User, ReadonlyArray<ToDoList>>

export const findToDoList =
  (store: ToDoListStore): ToDoListFetcher =>
  (user, listName) =>
    Option.fromUndefinedOr(store.get(user)?.find((list) => list.listName === listName))
