import type { Option } from "effect"
import type { ToDoListCommand } from "../../src/domain/commands.ts"
import type { ToDoListEvent } from "../../src/domain/events.ts"
import type { ListName, User } from "../../src/domain/todo-list.ts"

// 業務の言葉の操作です。HTTP も HTML も現れません。
export interface ZettaiDriver {
  // 断られたコマンドは、失敗にします。
  readonly send: (command: ToDoListCommand) => Promise<void>
  readonly itemsOf: (
    user: User,
    listName: ListName
  ) => Promise<Option.Option<ReadonlyArray<string>>>
}

// 経路は、それまでに起きた出来事と、driver を使う処理を受け取る高階関数です。
export type Route = <A>(
  history: ReadonlyArray<ToDoListEvent>,
  use: (zettai: ZettaiDriver) => Promise<A>
) => Promise<A>
