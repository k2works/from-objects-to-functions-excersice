import type { ToDoListEvent } from "./events.ts"
import { apply } from "./state.ts"
import type { ToDoListState } from "./state.ts"

// 状態から状態への変換です。
export type StateTransition = (state: ToDoListState) => ToDoListState

// 何もしない変換です。合成の単位元です。
export const identityTransition: StateTransition = (state) => state

// first の後に next を適用する、1 つの変換を作ります。
export const andThen =
  (first: StateTransition, next: StateTransition): StateTransition =>
  (state) =>
    next(first(state))

export const transitionOf =
  (event: ToDoListEvent): StateTransition =>
  (state) =>
    apply(state, event)

// 出来事の列を、1 つの変換に畳み込みます。
export const transitionOfAll = (events: ReadonlyArray<ToDoListEvent>): StateTransition =>
  events.map(transitionOf).reduce(andThen, identityTransition)
