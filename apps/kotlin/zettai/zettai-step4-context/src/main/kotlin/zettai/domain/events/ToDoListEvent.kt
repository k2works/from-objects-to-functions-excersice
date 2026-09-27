package zettai.domain.events

import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoList
import zettai.domain.ToDoStatus
import zettai.domain.User

/**
 * ToDo リストに起きた出来事。
 *
 * 状態を上書きするのではなく、起きたことを並べて残す。
 * 現在の状態は、出来事を順に適用した結果として得られる。
 */
sealed interface ToDoListEvent {
    val user: User
    val listName: ListName
}

data class ListCreated(override val user: User, override val listName: ListName) : ToDoListEvent

data class ItemAdded(
    override val user: User,
    override val listName: ListName,
    val item: ToDoItem
) : ToDoListEvent

data class ListRenamed(
    override val user: User,
    override val listName: ListName,
    val newName: ListName
) : ToDoListEvent

data class ItemStatusChanged(
    override val user: User,
    override val listName: ListName,
    val description: String,
    val newStatus: ToDoStatus
) : ToDoListEvent
