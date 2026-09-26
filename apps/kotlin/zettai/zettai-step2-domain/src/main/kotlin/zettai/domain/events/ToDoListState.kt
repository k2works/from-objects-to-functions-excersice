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
        is ListCreated -> copy(lists = lists + ((event.user to event.listName) to ToDoList(event.listName, emptyList())))
        is ItemAdded -> {
            val key = event.user to event.listName
            val current = lists[key]
            if (current == null) this else copy(lists = lists + (key to current.copy(items = current.items + event.item)))
        }
    }
