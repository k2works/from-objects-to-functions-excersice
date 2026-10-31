package zettai.acceptance

import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoList
import zettai.domain.User

/** 同じ scenario を異なる実行経路で動かすための操作。経路の差はここの外に出さない。 */
data class ZettaiActions(
    val name: String,
    val setUp: suspend (User, ToDoList) -> Unit,
    val getToDoList: suspend (User, ListName) -> List<ToDoItem>?,
)
