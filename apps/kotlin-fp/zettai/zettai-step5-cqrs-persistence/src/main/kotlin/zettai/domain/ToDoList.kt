package zettai.domain

import java.time.LocalDate

@JvmInline
value class User(val name: String)

@JvmInline
value class ListName(val name: String)

enum class ToDoStatus {
    Todo,
    InProgress,
    Done,
    Blocked,
}

data class ToDoItem(
    val description: String,
    val dueDate: LocalDate? = null,
    val status: ToDoStatus = ToDoStatus.Todo,
)

data class ToDoList(
    val listName: ListName,
    val items: List<ToDoItem>,
)
