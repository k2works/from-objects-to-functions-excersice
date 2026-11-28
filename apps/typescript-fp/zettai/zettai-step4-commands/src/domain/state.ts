import { Option } from "effect"
import type { ItemStatusChanged, ToDoListEvent } from "./events.ts"
import type { ListName, ToDoItem, ToDoList, User } from "./todo-list.ts"

// 利用者ごとの、入れ子の Map です。利用者と名前の組を、1 つのキーにはしません。
export type ToDoListState = ReadonlyMap<User, ReadonlyMap<ListName, ToDoList>>

export const emptyState: ToDoListState = new Map()

export const listIn = (
  state: ToDoListState,
  user: User,
  listName: ListName
): Option.Option<ToDoList> => Option.fromUndefinedOr(state.get(user)?.get(listName))

// 元の Map は変えず、変わる道筋の Map だけを作り直します。
const put = (state: ToDoListState, user: User, list: ToDoList): ToDoListState =>
  new Map(state).set(user, new Map(state.get(user)).set(list.listName, list))

// 同じ説明の項目が複数あれば、最初の 1 件だけを変えます。項目が無ければ undefined です。
const withStatus = (
  items: ReadonlyArray<ToDoItem>,
  { description, newStatus }: ItemStatusChanged
): ReadonlyArray<ToDoItem> | undefined => {
  const index = items.findIndex((item) => item.description === description)
  const target = items[index]
  return target === undefined ? undefined : items.with(index, { ...target, status: newStatus })
}

// 出来事を 1 件適用して、次の状態を返します。
export const apply = (state: ToDoListState, event: ToDoListEvent): ToDoListState => {
  switch (event._tag) {
    case "ListCreated":
      return put(state, event.user, { listName: event.listName, items: [] })
    case "ItemAdded":
      // 作られていないリストへの追加は、状態を変えません。不正な履歴の拒否は、ここではしません。
      return Option.match(listIn(state, event.user, event.listName), {
        onNone: () => state,
        onSome: (list) => put(state, event.user, { ...list, items: [...list.items, event.item] })
      })
    case "ItemStatusChanged":
      return Option.match(listIn(state, event.user, event.listName), {
        onNone: () => state,
        onSome: (list) => {
          const items = withStatus(list.items, event)
          return items === undefined ? state : put(state, event.user, { ...list, items })
        }
      })
  }
}
