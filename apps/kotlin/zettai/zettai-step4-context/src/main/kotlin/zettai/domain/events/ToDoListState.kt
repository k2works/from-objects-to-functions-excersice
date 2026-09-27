package zettai.domain.events

import zettai.domain.ListName
import zettai.domain.ToDoList
import zettai.domain.User

/** 出来事を適用した結果としての状態。 */
data class ToDoListState(val lists: Map<Pair<User, ListName>, ToDoList>) {

    fun listFor(user: User, listName: ListName): ToDoList? = lists[user to listName]

    companion object {
        val empty = ToDoListState(emptyMap())
    }
}

/** 1 つの出来事を状態に適用する。 */
fun ToDoListState.apply(event: ToDoListEvent): ToDoListState =
    when (event) {
        is ListCreated ->
            copy(lists = lists + (event.key() to ToDoList(event.listName, emptyList())))
        is ItemAdded -> {
            val current = lists[event.key()] ?: return this

            copy(lists = lists + (event.key() to current.copy(items = current.items + event.item)))
        }

        is ListRenamed -> {
            val current = lists[event.key()] ?: return this
            val renamed = current.copy(listName = event.newName)

            copy(lists = lists - event.key() + ((event.user to event.newName) to renamed))
        }

        is ItemStatusChanged -> {
            val current = lists[event.key()] ?: return this
            val updated = current.items.map {
                if (it.description == event.description) it.copy(status = event.newStatus) else it
            }

            copy(lists = lists + (event.key() to current.copy(items = updated)))
        }
    }

/** 状態の中でリストを引くための鍵。 */
private fun ToDoListEvent.key(): Pair<User, ListName> = user to listName
