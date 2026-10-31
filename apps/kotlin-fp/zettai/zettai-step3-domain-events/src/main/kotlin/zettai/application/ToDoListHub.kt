package zettai.application

import zettai.domain.ListName
import zettai.domain.ToDoList
import zettai.domain.User

typealias ToDoListFetcher = suspend (User, ListName) -> ToDoList?

data class ToDoListHub(
    val getList: ToDoListFetcher,
)

fun todoListHub(fetchList: ToDoListFetcher): ToDoListHub =
    ToDoListHub(getList = fetchList)
