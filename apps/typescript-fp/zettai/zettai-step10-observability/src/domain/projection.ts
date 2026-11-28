import type { ToDoListEvent } from "./events.ts"
import { withStatus } from "./state.ts"
import type { ListName, ToDoItem, User } from "./todo-list.ts"

// 一覧の画面が欲しい形です。コマンドを決めるための状態とは、別に持ちます。
export interface ToDoListRow {
  readonly listName: ListName
  readonly itemCount: number
  readonly doneCount: number
}

type Lists = ReadonlyMap<ListName, ReadonlyArray<ToDoItem>>

// 状態の畳み込みと同じ出来事を、別の形に畳み込みます。Map は、キーを足した順を保ちます。
const step = (lists: Lists, event: ToDoListEvent): Lists => {
  switch (event._tag) {
    case "ListCreated":
      return new Map(lists).set(event.listName, [])
    case "ItemAdded": {
      const items = lists.get(event.listName)
      return items === undefined
        ? lists
        : new Map(lists).set(event.listName, [...items, event.item])
    }
    case "ItemStatusChanged": {
      const items = lists.get(event.listName)
      const changed = items === undefined ? undefined : withStatus(items, event)
      return changed === undefined ? lists : new Map(lists).set(event.listName, changed)
    }
    case "ListRenamed":
      return new Map([...lists].filter(([name]) => name !== event.listName))
  }
}

const toRow = ([listName, items]: [ListName, ReadonlyArray<ToDoItem>]): ToDoListRow => ({
  listName,
  itemCount: items.length,
  doneCount: items.filter((item) => item.status === "Done").length
})

// 入力が利用者で絞られていなくても正しく動くように、ここでも絞ります。
export const projectRows = (
  user: User,
  events: ReadonlyArray<ToDoListEvent>
): ReadonlyArray<ToDoListRow> =>
  [...events.filter((event) => event.user === user).reduce(step, new Map())].map(toRow)
