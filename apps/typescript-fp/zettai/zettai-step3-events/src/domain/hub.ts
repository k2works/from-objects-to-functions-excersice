import type { ToDoListFetcher } from "./find-todo-list.ts"

// ドメインの入口です。web は、この型の操作だけを呼びます。
export interface ToDoListHub {
  readonly getList: ToDoListFetcher
}

// 依存は、関数の引数で受け取ります。
export const makeHub = (fetch: ToDoListFetcher): ToDoListHub => ({ getList: fetch })
