package zettai.domain.events

tailrec fun List<ToDoListEvent>.replayByRecursion(
    state: ToDoListState = ToDoListState.empty,
): ToDoListState =
    if (isEmpty()) {
        state
    } else {
        drop(1).replayByRecursion(state.apply(first()))
    }

fun List<ToDoListEvent>.replayFrom(
    initial: ToDoListState = ToDoListState.empty,
): ToDoListState =
    fold(initial) { state, event -> state.apply(event) }

typealias StateTransition = (ToDoListState) -> ToDoListState

val identityTransition: StateTransition = { it }

infix fun StateTransition.andThen(next: StateTransition): StateTransition =
    { state -> next(this(state)) }

fun ToDoListEvent.asTransition(): StateTransition = { state -> state.apply(this) }

fun List<ToDoListEvent>.asTransition(): StateTransition =
    fold(identityTransition) { transition, event ->
        transition andThen event.asTransition()
    }
