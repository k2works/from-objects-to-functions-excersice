import type { Option } from "effect"
import type { ToDoListStore } from "../../src/domain/find-todo-list.ts"
import type { ListName, User } from "../../src/domain/todo-list.ts"

// 業務の言葉の操作です。HTTP も HTML も現れません。
export interface ZettaiDriver {
  readonly itemsOf: (
    user: User,
    listName: ListName
  ) => Promise<Option.Option<ReadonlyArray<string>>>
}

// 経路は、保管するリストと、driver を使う処理を受け取る高階関数です。
export type Route = <A>(
  store: ToDoListStore,
  use: (zettai: ZettaiDriver) => Promise<A>
) => Promise<A>
