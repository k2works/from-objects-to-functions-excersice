package zettai.domain.events

import zettai.domain.ListName
import zettai.domain.ToDoList
import zettai.domain.User

class ToDoListState private constructor(
    private val lists: Map<Pair<User, ListName>, ToDoList>,
) {
    fun listFor(user: User, listName: ListName): ToDoList? =
        lists[user to listName]

    internal fun put(user: User, listName: ListName, todoList: ToDoList): ToDoListState =
        from(lists + ((user to listName) to todoList))

    override fun equals(other: Any?): Boolean =
        this === other || other is ToDoListState && lists == other.lists

    override fun hashCode(): Int = lists.hashCode()

    override fun toString(): String = "ToDoListState(lists=$lists)"

    companion object {
        val empty = ToDoListState(emptyMap())

        fun from(lists: Map<Pair<User, ListName>, ToDoList>): ToDoListState {
            val snapshot = lists
                .mapValues { (_, todoList) ->
                    todoList.copy(items = todoList.items.toList())
                }
                .toMap()

            return ToDoListState(snapshot)
        }
    }
}

fun ToDoListState.apply(event: ToDoListEvent): ToDoListState {
    return when (event) {
        is ListCreated -> put(
            event.user,
            event.listName,
            ToDoList(event.listName, emptyList()),
        )

        is ItemAdded -> {
            val current = listFor(event.user, event.listName) ?: return this
            put(
                event.user,
                event.listName,
                current.copy(items = current.items + event.item),
            )
        }
    }
}
