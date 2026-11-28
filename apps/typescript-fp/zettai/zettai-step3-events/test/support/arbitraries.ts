import fc from "fast-check"
import { itemAdded, listCreated } from "../../src/domain/events.ts"
import type { ToDoListEvent } from "../../src/domain/events.ts"
import { makeToDoItem } from "../../src/domain/todo-list.ts"
import { listNameOf, userOf } from "./model.ts"

// 名前の候補を少なくします。多いと、作ったリストに項目が足されることが、ほとんど起きません。
const user = fc.constantFrom("uberto", "frank").map(userOf)
const listName = fc.constantFrom("book", "a/b").map(listNameOf)
const item = fc.constantFrom("milk", "eggs", "bread").map((text) => makeToDoItem(text))

export const event: fc.Arbitrary<ToDoListEvent> = fc.oneof(
  fc.tuple(user, listName).map(([u, l]) => listCreated(u, l)),
  fc.tuple(user, listName, item).map(([u, l, i]) => itemAdded(u, l, i))
)

export const events = fc.array(event, { maxLength: 12 })
