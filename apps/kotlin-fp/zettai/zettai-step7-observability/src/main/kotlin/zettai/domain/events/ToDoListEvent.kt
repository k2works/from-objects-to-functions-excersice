package zettai.domain.events

import zettai.domain.ListName
import zettai.domain.StreamId
import zettai.domain.ToDoItem
import zettai.domain.ToDoStatus
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

data class ItemStatusChanged(
    override val user: User,
    override val listName: ListName,
    val description: String,
    val newStatus: ToDoStatus,
) : ToDoListEvent

/** `listName` が旧い名前。旧い stream に追記され、新しい名前の stream には別に `ListCreated` が追記される。 */
data class ListRenamed(
    override val user: User,
    override val listName: ListName,
    val newName: ListName,
) : ToDoListEvent

fun ToDoListEvent.streamId(): StreamId = StreamId(user, listName)
