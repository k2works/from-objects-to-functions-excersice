import type { ListName, ToDoStatus, User } from "./todo-list.ts"

// 業務の上で、起こりうる失敗です。理由は _tag で区別します。
export interface ListAlreadyExists {
  readonly _tag: "ListAlreadyExists"
  readonly user: User
  readonly listName: ListName
}

export interface ListNotFound {
  readonly _tag: "ListNotFound"
  readonly user: User
  readonly listName: ListName
}

export interface ItemNotFound {
  readonly _tag: "ItemNotFound"
  readonly user: User
  readonly listName: ListName
  readonly description: string
}

export interface InvalidTransition {
  readonly _tag: "InvalidTransition"
  readonly description: string
  readonly from: ToDoStatus
  readonly to: ToDoStatus
}

export type ZettaiError = ListAlreadyExists | ListNotFound | ItemNotFound | InvalidTransition
