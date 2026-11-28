import type { ListName, ToDoItem, ToDoStatus, User } from "./todo-list.ts"

// 起きたことを表します。名前は過去形です。_tag で種類を区別します。
export interface ListCreated {
  readonly _tag: "ListCreated"
  readonly user: User
  readonly listName: ListName
}

export interface ItemAdded {
  readonly _tag: "ItemAdded"
  readonly user: User
  readonly listName: ListName
  readonly item: ToDoItem
}

export interface ItemStatusChanged {
  readonly _tag: "ItemStatusChanged"
  readonly user: User
  readonly listName: ListName
  readonly description: string
  readonly newStatus: ToDoStatus
}

export type ToDoListEvent = ListCreated | ItemAdded | ItemStatusChanged

export const listCreated = (user: User, listName: ListName): ToDoListEvent => ({
  _tag: "ListCreated",
  user,
  listName
})

export const itemAdded = (user: User, listName: ListName, item: ToDoItem): ToDoListEvent => ({
  _tag: "ItemAdded",
  user,
  listName,
  item
})

export const itemStatusChanged = (
  user: User,
  listName: ListName,
  description: string,
  newStatus: ToDoStatus
): ToDoListEvent => ({ _tag: "ItemStatusChanged", user, listName, description, newStatus })
