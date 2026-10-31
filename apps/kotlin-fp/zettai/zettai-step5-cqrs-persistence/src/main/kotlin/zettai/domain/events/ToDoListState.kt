package zettai.domain.events

import zettai.domain.ListName
import zettai.domain.ToDoList
import zettai.domain.User

class ToDoListState private constructor(
    private val lists: Map<Pair<User, ListName>, ToDoList>,
) {
    fun listFor(user: User, listName: ListName): ToDoList? = lists[user to listName]

    internal fun put(key: Pair<User, ListName>, list: ToDoList): ToDoListState =
        ToDoListState(lists + (key to list))

    override fun equals(other: Any?): Boolean =
        other is ToDoListState && lists == other.lists

    override fun hashCode(): Int = lists.hashCode()

    override fun toString(): String = "ToDoListState(lists=$lists)"

    companion object {
        val empty: ToDoListState = ToDoListState(emptyMap())

        fun from(lists: Map<Pair<User, ListName>, ToDoList>): ToDoListState =
            ToDoListState(
                lists.mapValues { (_, list) ->
                    list.copy(items = list.items.toList())
                }.toMap(),
            )
    }
}

fun ToDoListState.apply(event: ToDoListEvent): ToDoListState =
    when (event) {
        is ListCreated ->
            put(event.key(), ToDoList(event.listName, emptyList()))

        is ItemAdded -> {
            val current = listFor(event.user, event.listName) ?: return this
            put(event.key(), current.copy(items = current.items + event.item))
        }

        is ItemStatusChanged -> {
            val current = listFor(event.user, event.listName) ?: return this
            val targetIndex = current.items.indexOfFirst {
                it.description == event.description
            }
            if (targetIndex < 0) return this
            val updated = current.items.mapIndexed { index, item ->
                if (index == targetIndex) {
                    item.copy(status = event.newStatus)
                } else {
                    item
                }
            }
            put(event.key(), current.copy(items = updated))
        }
    }

private fun ToDoListEvent.key(): Pair<User, ListName> = user to listName
