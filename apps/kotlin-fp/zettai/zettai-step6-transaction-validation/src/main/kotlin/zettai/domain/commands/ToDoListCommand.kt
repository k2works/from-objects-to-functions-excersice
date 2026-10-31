package zettai.domain.commands

import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoStatus
import zettai.domain.User

sealed interface ToDoListCommand {
    val user: User
    val listName: ListName
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
