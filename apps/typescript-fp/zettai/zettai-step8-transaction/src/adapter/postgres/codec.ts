import { Option, Result, Schema } from "effect"
import { itemAdded, itemStatusChanged, listCreated } from "../../domain/events.ts"
import type { ToDoListEvent } from "../../domain/events.ts"
import {
  makeToDoItem,
  parseDueDate,
  parseListName,
  parseUser,
  TODO_STATUSES
} from "../../domain/todo-list.ts"
import type { ListName, User } from "../../domain/todo-list.ts"

// 行の payload の形です。形の検査は Schema が、値の意味の検査は domain の生成の関数がします。
const names = { user: Schema.String, listName: Schema.String }
const status = Schema.Literals(TODO_STATUSES)

const ListCreatedPayload = Schema.Struct(names)

const ItemAddedPayload = Schema.Struct({
  ...names,
  item: Schema.Struct({
    description: Schema.String,
    status,
    dueDate: Schema.NullOr(Schema.String)
  })
})

const ItemStatusChangedPayload = Schema.Struct({
  ...names,
  description: Schema.String,
  newStatus: status
})

type Decoded = Result.Result<ToDoListEvent, unknown>

const domainNames = (raw: { readonly user: string; readonly listName: string }) =>
  Result.all({ user: parseUser(raw.user), listName: parseListName(raw.listName) })

const optionalDueDate = (raw: string | null) =>
  raw === null ? Result.succeed(undefined) : parseDueDate(raw)

const toItemAdded = (raw: typeof ItemAddedPayload.Type): Decoded =>
  Result.all({
    names: domainNames(raw),
    dueDate: optionalDueDate(raw.item.dueDate)
  }).pipe(
    Result.map(({ names: { user, listName }, dueDate }) =>
      itemAdded(
        user,
        listName,
        makeToDoItem(raw.item.description, {
          status: raw.item.status,
          ...(dueDate === undefined ? {} : { dueDate })
        })
      )
    )
  )

const withNames = <Raw extends { readonly user: string; readonly listName: string }>(
  raw: Raw,
  build: (user: User, listName: ListName) => ToDoListEvent
): Decoded => domainNames(raw).pipe(Result.map(({ user, listName }) => build(user, listName)))

// 出来事の種類を足すと、キーが足りないので型検査が失敗します。
const decoders: Record<ToDoListEvent["_tag"], (payload: unknown) => Decoded> = {
  ListCreated: (payload) =>
    Schema.decodeUnknownResult(ListCreatedPayload)(payload).pipe(
      Result.flatMap((raw) => withNames(raw, listCreated))
    ),
  ItemAdded: (payload) =>
    Schema.decodeUnknownResult(ItemAddedPayload)(payload).pipe(Result.flatMap(toItemAdded)),
  ItemStatusChanged: (payload) =>
    Schema.decodeUnknownResult(ItemStatusChangedPayload)(payload).pipe(
      Result.flatMap((raw) =>
        withNames(raw, (user, listName) =>
          itemStatusChanged(user, listName, raw.description, raw.newStatus)
        )
      )
    )
}

const isEventType = (type: string): type is ToDoListEvent["_tag"] => Object.hasOwn(decoders, type)

export const decodeEvent = (eventType: string, payload: unknown): Decoded =>
  isEventType(eventType)
    ? decoders[eventType](payload)
    : Result.fail(`未知の出来事の種類です: ${eventType}`)

export const encodePayload = (event: ToDoListEvent): object => {
  const { user, listName } = event
  switch (event._tag) {
    case "ListCreated":
      return { user, listName }
    case "ItemAdded": {
      const { description, status: itemStatus, dueDate } = event.item
      return {
        user,
        listName,
        item: { description, status: itemStatus, dueDate: Option.getOrNull(dueDate) }
      }
    }
    case "ItemStatusChanged":
      return { user, listName, description: event.description, newStatus: event.newStatus }
  }
}

// "/" で区切る前に、名前の中の "%" と "/" を置き換えます。
const escapePart = (part: string): string => part.replaceAll("%", "%25").replaceAll("/", "%2F")

export const userPrefix = (user: User): string => `${escapePart(user)}/`

export const entityId = (user: User, listName: ListName): string =>
  `${userPrefix(user)}${escapePart(listName)}`
