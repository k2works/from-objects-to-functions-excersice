package zettai.acceptance

import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoList
import zettai.domain.User

data class ZettaiActions(
    val name: String,
    val setUp: (User, ToDoList) -> Unit,
    val getToDoList: (User, ListName) -> List<ToDoItem>?,
)
