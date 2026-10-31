package zettai.domain.events

import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.User

sealed interface ToDoListEvent {
    val user: User
    val listName: ListName
}

data class ListCreated(
    override val user: User,
    override val listName: ListName,
) : ToDoListEvent

data class ItemAdded(
    override val user: User,
    override val listName: ListName,
    val item: ToDoItem,
) : ToDoListEvent
