package zettai.domain.events

fun List<ToDoListEvent>.replayByRecursion(from: ToDoListState): ToDoListState =
    when {
        isEmpty() -> from
        else -> drop(1).replayByRecursion(from.apply(first()))
    }

fun List<ToDoListEvent>.replayFrom(from: ToDoListState): ToDoListState =
    fold(from) { state, event -> state.apply(event) }

typealias StateTransition = (ToDoListState) -> ToDoListState

val identityTransition: StateTransition = { it }

infix fun StateTransition.andThen(next: StateTransition): StateTransition =
    { state -> next(this(state)) }

fun ToDoListEvent.asTransition(): StateTransition =
    { state -> state.apply(this) }

fun List<ToDoListEvent>.asTransition(): StateTransition =
    fold(identityTransition) { accumulated, event ->
        accumulated andThen event.asTransition()
    }
