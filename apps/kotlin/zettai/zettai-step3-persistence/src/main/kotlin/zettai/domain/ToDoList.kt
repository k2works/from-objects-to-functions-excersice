package zettai.domain

import java.time.LocalDate

/**
 * ToDo リストのドメイン。
 *
 * このパッケージはフレームワークを知らない。http4k の型を import しないことが、
 * ドメインとインフラストラクチャの境界そのものになる。
 */
data class User(val name: String)

data class ListName(val name: String)

enum class ToDoStatus { Todo, InProgress, Done, Blocked }

data class ToDoItem(
    val description: String,
    val dueDate: LocalDate? = null,
    val status: ToDoStatus = ToDoStatus.Todo
)

data class ToDoList(val listName: ListName, val items: List<ToDoItem>)
