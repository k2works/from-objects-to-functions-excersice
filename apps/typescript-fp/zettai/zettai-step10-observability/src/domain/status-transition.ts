import type { ToDoStatus } from "./todo-list.ts"

// 現在の状態ごとに、移れる状態を並べます。同じ状態への遷移は、どこにもありません。
const nextStatuses: Record<ToDoStatus, ReadonlyArray<ToDoStatus>> = {
  Todo: ["InProgress", "Done"],
  InProgress: ["Done", "Blocked"],
  Done: [],
  Blocked: ["InProgress"]
}

export const canTransitionTo = (from: ToDoStatus, to: ToDoStatus): boolean =>
  nextStatuses[from].includes(to)
