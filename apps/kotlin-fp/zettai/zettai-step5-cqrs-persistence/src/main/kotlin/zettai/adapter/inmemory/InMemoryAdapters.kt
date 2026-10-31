package zettai.adapter.inmemory

import arrow.core.right
import zettai.application.AggregateEventReader
import zettai.application.EventPersister
import zettai.application.UserEventReader
import zettai.domain.events.ToDoListEvent

data class InMemoryEventStore(
    val readAggregate: AggregateEventReader,
    val readUser: UserEventReader,
    val persist: EventPersister,
)

/** 単一実行用の学習 adapter。並行 command の atomicity は保証しない。 */
fun inMemoryEventStore(
    initialEvents: List<ToDoListEvent> = emptyList(),
): InMemoryEventStore {
    val events = initialEvents.toMutableList()

    return InMemoryEventStore(
        readAggregate = { user, listName ->
            events.filter { it.user == user && it.listName == listName }.right()
        },
        readUser = { user -> events.filter { it.user == user }.right() },
        persist = { newEvents ->
            events += newEvents.toList()
            Unit.right()
        },
    )
}
