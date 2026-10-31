package zettai.adapter.inmemory

import arrow.core.right
import zettai.application.EventStore
import zettai.domain.events.ToDoListEvent

/** 単一実行用の学習 adapter。並行 command の atomicity は保証しない。 */
fun inMemoryEventStore(
    initialEvents: List<ToDoListEvent> = emptyList(),
): EventStore {
    val events = initialEvents.toMutableList()

    return EventStore(
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
