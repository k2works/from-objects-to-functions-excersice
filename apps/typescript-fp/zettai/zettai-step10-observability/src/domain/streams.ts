import type { ToDoListCommand } from "./commands.ts"
import type { ToDoListEvent } from "./events.ts"
import type { ListName, User } from "./todo-list.ts"

// 出来事の列（ストリーム）は、利用者とリスト名の組で決まります。
export interface StreamId {
  readonly user: User
  readonly listName: ListName
}

export const streamOf = (user: User, listName: ListName): StreamId => ({ user, listName })

// コマンドが読み書きするストリームです。名前の変更は、古い名前と新しい名前の 2 つです。
export const streamsFor = (command: ToDoListCommand): ReadonlyArray<StreamId> =>
  command._tag === "RenameToDoList"
    ? [streamOf(command.user, command.listName), streamOf(command.user, command.newName)]
    : [streamOf(command.user, command.listName)]

export const belongsTo = (event: ToDoListEvent, streams: ReadonlyArray<StreamId>): boolean =>
  streams.some((stream) => stream.user === event.user && stream.listName === event.listName)

// 出来事の列が触れるストリームを、重複なく返します。
export const streamsOf = (events: ReadonlyArray<ToDoListEvent>): ReadonlyArray<StreamId> =>
  events.reduce<ReadonlyArray<StreamId>>(
    (streams, event) =>
      belongsTo(event, streams) ? streams : [...streams, streamOf(event.user, event.listName)],
    []
  )
