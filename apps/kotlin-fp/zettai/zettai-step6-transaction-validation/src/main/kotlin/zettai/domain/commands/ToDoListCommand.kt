package zettai.domain.commands

import zettai.domain.ListName
import zettai.domain.StreamId
import zettai.domain.ToDoItem
import zettai.domain.ToDoStatus
import zettai.domain.User

sealed interface ToDoListCommand {
    val user: User
    val listName: ListName

    /** command が触れる stream。transaction の中で、これらを読んで判断し、書く。 */
    val streams: List<StreamId> get() = listOf(StreamId(user, listName))
}

data class CreateToDoList(
    override val user: User,
    override val listName: ListName,
) : ToDoListCommand

data class AddToDoItem(
    override val user: User,
    override val listName: ListName,
    val item: ToDoItem,
) : ToDoListCommand

data class ChangeItemStatus(
    override val user: User,
    override val listName: ListName,
    val description: String,
    val newStatus: ToDoStatus,
) : ToDoListCommand
