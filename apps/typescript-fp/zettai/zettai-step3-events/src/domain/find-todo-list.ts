import type { Option } from "effect"
import { listIn } from "./state.ts"
import type { ToDoListState } from "./state.ts"
import type { ListName, ToDoList, User } from "./todo-list.ts"

export type ToDoListFetcher = (user: User, listName: ListName) => Option.Option<ToDoList>

export const findToDoList =
  (state: ToDoListState): ToDoListFetcher =>
  (user, listName) =>
    listIn(state, user, listName)
