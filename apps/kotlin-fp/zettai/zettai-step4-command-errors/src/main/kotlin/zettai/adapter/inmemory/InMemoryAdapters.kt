package zettai.adapter.inmemory

import arrow.core.left
import arrow.core.right
import zettai.application.EventPersister
import zettai.application.StateFetcher
import zettai.application.ToDoListFetcher
import zettai.domain.ToDoList
import zettai.domain.User
import zettai.domain.errors.ListNotFound
import zettai.domain.events.ToDoListEvent
import zettai.domain.events.ToDoListState
import zettai.domain.events.replayFrom

fun inMemoryFetcher(lists: Map<User, List<ToDoList>>): ToDoListFetcher {
    val snapshot = lists
        .mapValues { (_, todoLists) ->
            todoLists.map { list ->
                list.copy(items = list.items.toList())
            }
        }
        .toMap()

    return { user, listName ->
        val found = snapshot[user]?.firstOrNull { it.listName == listName }

        found
            ?.copy(items = found.items.toList())
            ?.right()
            ?: ListNotFound(user, listName).left()
    }
}

data class InMemoryEventStore(
    val fetchState: StateFetcher,
    val persist: EventPersister,
)

fun inMemoryEventStore(
    initialEvents: List<ToDoListEvent> = emptyList(),
): InMemoryEventStore {
    val events = initialEvents.toMutableList()

    return InMemoryEventStore(
        fetchState = { events.toList().replayFrom(ToDoListState.empty) },
        persist = { newEvents -> events += newEvents.toList() },
    )
}
