import fc from "fast-check"
import { addToDoItem, changeItemStatus, createToDoList } from "../../src/domain/commands.ts"
import type { ToDoListCommand } from "../../src/domain/commands.ts"
import type { ToDoStatus } from "../../src/domain/todo-list.ts"
import { itemAdded, itemStatusChanged, listCreated } from "../../src/domain/events.ts"
import type { ToDoListEvent } from "../../src/domain/events.ts"
import { makeToDoItem } from "../../src/domain/todo-list.ts"
import { createdWith } from "./history.ts"
import { listNameOf, userOf } from "./model.ts"

// 名前の候補を少なくします。多いと、作ったリストに項目が足されることが、ほとんど起きません。
const user = fc.constantFrom("uberto", "frank").map(userOf)
const listName = fc.constantFrom("book", "a/b").map(listNameOf)
const item = fc.constantFrom("milk", "eggs", "bread").map((text) => makeToDoItem(text))

const status = fc.constantFrom<ToDoStatus>("Todo", "InProgress", "Done", "Blocked")
const description = fc.constantFrom("milk", "eggs")

export const event: fc.Arbitrary<ToDoListEvent> = fc.oneof(
  fc.tuple(user, listName).map(([u, l]) => listCreated(u, l)),
  fc.tuple(user, listName, item).map(([u, l, i]) => itemAdded(u, l, i)),
  fc.tuple(user, listName, description, status).map(([u, l, d, s]) => itemStatusChanged(u, l, d, s))
)

export const events = fc.array(event, { maxLength: 12 })

// 上の列だけでは、完了した項目がほとんど現れません。項目のあるリストを先に置き、状態の変更を多めに混ぜます。
const finish = fc
  .tuple(listName, description)
  .map(([l, d]) => itemStatusChanged(userOf("uberto"), l, d, "Done"))

export const busyEvents = fc
  .array(fc.oneof(event, finish), { maxLength: 12 })
  .map((history) => [...createdWith("uberto", "book", "milk", "eggs"), ...history])

// コマンドも、少ない候補から生成します。受け付けと拒否の両方が起きるようにします。
export const command: fc.Arbitrary<ToDoListCommand> = fc.oneof(
  fc.tuple(user, listName).map(([u, l]) => createToDoList(u, l)),
  fc.tuple(user, listName, item).map(([u, l, i]) => addToDoItem(u, l, i)),
  fc.tuple(user, listName, description, status).map(([u, l, d, s]) => changeItemStatus(u, l, d, s))
)

export const commands = fc.array(command, { minLength: 10, maxLength: 40 })
