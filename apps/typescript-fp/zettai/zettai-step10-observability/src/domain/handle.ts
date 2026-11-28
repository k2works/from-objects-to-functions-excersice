import { Option, Result } from "effect"
import type {
  AddToDoItem,
  ChangeItemStatus,
  CreateToDoList,
  RenameToDoList,
  ToDoListCommand
} from "./commands.ts"
import { listNotFound } from "./errors.ts"
import type { ZettaiError } from "./errors.ts"
import { itemAdded, itemStatusChanged, listCreated, listRenamed } from "./events.ts"
import type { ToDoListEvent } from "./events.ts"
import { listIn } from "./state.ts"
import type { ToDoListState } from "./state.ts"
import { canTransitionTo } from "./status-transition.ts"
import type { ListName, ToDoList, User } from "./todo-list.ts"

// 受け付けたら起こす出来事を、断るなら理由を返します。
export type Decision = Result.Result<ReadonlyArray<ToDoListEvent>, ZettaiError>

const existingList = (
  state: ToDoListState,
  user: User,
  listName: ListName
): Result.Result<ToDoList, ZettaiError> =>
  Result.fromOption(listIn(state, user, listName), () => listNotFound(user, listName))

const create = ({ user, listName }: CreateToDoList, state: ToDoListState): Decision =>
  Option.isSome(listIn(state, user, listName))
    ? Result.fail({ _tag: "ListAlreadyExists", user, listName })
    : Result.succeed([listCreated(user, listName)])

const add = ({ user, listName, item }: AddToDoItem, state: ToDoListState): Decision =>
  existingList(state, user, listName).pipe(Result.map(() => [itemAdded(user, listName, item)]))

const changeStatus = (command: ChangeItemStatus, state: ToDoListState): Decision => {
  const { user, listName, description, newStatus } = command
  return existingList(state, user, listName).pipe(
    Result.flatMap((list): Decision => {
      // apply が変えるのと同じく、説明が一致する最初の項目を調べます。
      const item = list.items.find((each) => each.description === description)
      if (item === undefined) {
        return Result.fail({ _tag: "ItemNotFound", user, listName, description })
      }
      return canTransitionTo(item.status, newStatus)
        ? Result.succeed([itemStatusChanged(user, listName, description, newStatus)])
        : Result.fail({ _tag: "InvalidTransition", description, from: item.status, to: newStatus })
    })
  )
}

// 古い名前を終わらせ、いまの項目を新しい名前に書きます。新しい名前は、空いていなければなりません。
const rename = ({ user, listName, newName }: RenameToDoList, state: ToDoListState): Decision =>
  existingList(state, user, listName).pipe(
    Result.flatMap((list): Decision =>
      Option.isSome(listIn(state, user, newName))
        ? Result.fail({ _tag: "ListAlreadyExists", user, listName: newName })
        : Result.succeed([
            listRenamed(user, listName, newName),
            listCreated(user, newName),
            ...list.items.map((item) => itemAdded(user, newName, item))
          ])
    )
  )

// コマンドと現在の状態から、結果を決めます。何も保存しない、純粋な関数です。
export const handle = (command: ToDoListCommand, state: ToDoListState): Decision => {
  switch (command._tag) {
    case "CreateToDoList":
      return create(command, state)
    case "AddToDoItem":
      return add(command, state)
    case "ChangeItemStatus":
      return changeStatus(command, state)
    case "RenameToDoList":
      return rename(command, state)
  }
}
