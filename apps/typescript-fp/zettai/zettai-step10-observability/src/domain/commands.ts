import type { ListName, ToDoItem, ToDoStatus, User } from "./todo-list.ts"

// 利用者の意図を表します。名前は命令形です。イベントと違い、断られることがあります。
export interface CreateToDoList {
  readonly _tag: "CreateToDoList"
  readonly user: User
  readonly listName: ListName
}

export interface AddToDoItem {
  readonly _tag: "AddToDoItem"
  readonly user: User
  readonly listName: ListName
  readonly item: ToDoItem
}

export interface ChangeItemStatus {
  readonly _tag: "ChangeItemStatus"
  readonly user: User
  readonly listName: ListName
  readonly description: string
  readonly newStatus: ToDoStatus
}

export interface RenameToDoList {
  readonly _tag: "RenameToDoList"
  readonly user: User
  readonly listName: ListName
  readonly newName: ListName
}

export type ToDoListCommand = CreateToDoList | AddToDoItem | ChangeItemStatus | RenameToDoList

export const createToDoList = (user: User, listName: ListName): ToDoListCommand => ({
  _tag: "CreateToDoList",
  user,
  listName
})

export const addToDoItem = (user: User, listName: ListName, item: ToDoItem): ToDoListCommand => ({
  _tag: "AddToDoItem",
  user,
  listName,
  item
})

export const changeItemStatus = (
  user: User,
  listName: ListName,
  description: string,
  newStatus: ToDoStatus
): ToDoListCommand => ({ _tag: "ChangeItemStatus", user, listName, description, newStatus })

export const renameToDoList = (
  user: User,
  listName: ListName,
  newName: ListName
): ToDoListCommand => ({ _tag: "RenameToDoList", user, listName, newName })
