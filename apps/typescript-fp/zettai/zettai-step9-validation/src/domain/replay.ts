import type { ToDoListEvent } from "./events.ts"
import { apply, emptyState } from "./state.ts"
import type { ToDoListState } from "./state.ts"

// 先頭の 1 件を適用して、残りを同じ関数に渡します。件数だけ、呼び出しが積み重なります。
export const replayRecursively = (
  events: ReadonlyArray<ToDoListEvent>,
  state: ToDoListState = emptyState,
  index = 0
): ToDoListState => {
  const event = events[index]
  return event === undefined ? state : replayRecursively(events, apply(state, event), index + 1)
}

// 同じことを、reduce で書きます。呼び出しは積み重なりません。
export const replay = (events: ReadonlyArray<ToDoListEvent>): ToDoListState =>
  events.reduce(apply, emptyState)
