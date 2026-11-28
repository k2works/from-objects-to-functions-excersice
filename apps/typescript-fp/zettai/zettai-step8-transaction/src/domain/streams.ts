import type { ToDoListEvent } from "./events.ts"
import type { ListName, User } from "./todo-list.ts"

// 出来事の列（ストリーム）は、利用者とリスト名の組で決まります。
export interface StreamId {
  readonly user: User
  readonly listName: ListName
}

export const streamOf = (user: User, listName: ListName): StreamId => ({ user, listName })

export const belongsTo = (event: ToDoListEvent, streams: ReadonlyArray<StreamId>): boolean =>
  streams.some((stream) => stream.user === event.user && stream.listName === event.listName)

// 出来事の列が触れるストリームを、重複なく返します。
export const streamsOf = (events: ReadonlyArray<ToDoListEvent>): ReadonlyArray<StreamId> =>
  events.reduce<ReadonlyArray<StreamId>>(
    (streams, event) =>
      belongsTo(event, streams) ? streams : [...streams, streamOf(event.user, event.listName)],
    []
  )
