import { Result } from "effect"
import { makeToDoItem, parseListName, parseUser } from "../../src/domain/todo-list.ts"
import type { ListName, ToDoList, User } from "../../src/domain/todo-list.ts"

// テストの固定の値を作ります。誤った名前を書くと、テストが失敗します。
export const userOf = (raw: string): User => Result.getOrThrow(parseUser(raw))

export const listNameOf = (raw: string): ListName => Result.getOrThrow(parseListName(raw))

export const listOf = (name: string, ...descriptions: ReadonlyArray<string>): ToDoList => ({
  listName: listNameOf(name),
  items: descriptions.map((description) => makeToDoItem(description))
})
