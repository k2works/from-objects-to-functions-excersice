package zettai.domain.commands

import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoStatus
import zettai.domain.User

/**
 * 利用者の意図。
 *
 * コマンドは命令形で名付ける（CreateToDoList）。まだ起きていないので拒否できる。
 * イベントは過去形で名付ける（ListCreated）。すでに起きたので拒否できない。
 * この違いが、両方を持つ理由そのものである。
 */
sealed interface ToDoListCommand {
    val user: User
    val listName: ListName
}

data class CreateToDoList(override val user: User, override val listName: ListName) : ToDoListCommand

data class AddToDoItem(
    override val user: User,
    override val listName: ListName,
    val item: ToDoItem
) : ToDoListCommand

data class ChangeItemStatus(
    override val user: User,
    override val listName: ListName,
    val description: String,
    val newStatus: ToDoStatus
) : ToDoListCommand
