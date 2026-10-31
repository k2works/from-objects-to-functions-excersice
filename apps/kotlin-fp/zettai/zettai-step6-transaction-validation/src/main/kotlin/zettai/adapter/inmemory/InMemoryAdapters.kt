package zettai.adapter.inmemory

import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import zettai.application.EventStore
import zettai.application.StreamEvents
import zettai.domain.StreamId
import zettai.domain.errors.ConcurrentModification
import zettai.domain.errors.PersistenceFailure
import zettai.domain.events.ToDoListEvent
import zettai.domain.events.streamId

/**
 * 学習用の in-memory の event store。環境 [Tx] は `Unit`。
 * transaction は `Mutex` で直列にし、追記は版を確かめてからまとめて確定する。
 * PostgreSQL の store と同じ契約を満たす。
 */
fun inMemoryEventStore(
    initialEvents: List<ToDoListEvent> = emptyList(),
): EventStore<Unit> {
    val log = initialEvents.toMutableList()
    val mutex = Mutex()

    fun eventsOf(stream: StreamId): List<ToDoListEvent> = log.filter { it.streamId() == stream }

    return EventStore(
        readAggregate = { user, listName ->
            mutex.withLock { eventsOf(StreamId(user, listName)) }.right()
        },
        readUser = { user -> mutex.withLock { log.filter { it.user == user } }.right() },
        transaction = { block -> mutex.withLock { block(Unit) } },
        readStream = { _, stream ->
            val events = eventsOf(stream)
            StreamEvents(events, events.size).right()
        },
        append = { _, expected, events ->
            val touched = events.map { it.streamId() }.distinct()
            val missing = touched.filter { it !in expected }
            val stale = expected.filter { (stream, version) -> eventsOf(stream).size != version }.keys.toList()
            when {
                missing.isNotEmpty() -> PersistenceFailure("期待する版が無い stream: $missing").left()
                stale.isNotEmpty() -> ConcurrentModification(stale).left()
                else -> {
                    log += events.toList()
                    Unit.right()
                }
            }
        },
    )
}
