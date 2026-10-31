package zettai.domain.errors

import zettai.domain.ListName
import zettai.domain.ToDoStatus
import zettai.domain.User

sealed interface ZettaiError {
    val message: String
}

data class ListNotFound(
    val user: User,
    val listName: ListName,
) : ZettaiError {
    override val message: String = "ToDo list not found: ${user.name}/${listName.name}"
}

data class ListAlreadyExists(
    val user: User,
    val listName: ListName,
) : ZettaiError {
    override val message: String = "ToDo list already exists: ${user.name}/${listName.name}"
}

data class ItemNotFound(
    val user: User,
    val listName: ListName,
    val description: String,
) : ZettaiError {
    override val message: String =
        "ToDo item not found: ${user.name}/${listName.name}/$description"
}

data class InvalidTransition(
    val description: String,
    val from: ToDoStatus,
    val to: ToDoStatus,
) : ZettaiError {
    override val message: String = "Invalid transition for $description: $from -> $to"
}
