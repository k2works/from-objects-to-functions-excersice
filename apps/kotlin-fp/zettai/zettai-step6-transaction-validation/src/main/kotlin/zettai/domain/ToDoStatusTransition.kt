package zettai.domain

private val allowedTransitions = mapOf(
    ToDoStatus.Todo to setOf(ToDoStatus.InProgress, ToDoStatus.Done),
    ToDoStatus.InProgress to setOf(ToDoStatus.Done, ToDoStatus.Blocked),
    ToDoStatus.Done to emptySet(),
    ToDoStatus.Blocked to setOf(ToDoStatus.InProgress),
)

fun ToDoStatus.canTransitionTo(next: ToDoStatus): Boolean =
    next in allowedTransitions.getValue(this)
