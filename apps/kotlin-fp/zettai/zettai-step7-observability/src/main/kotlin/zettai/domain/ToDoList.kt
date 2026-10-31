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

/**
 * 説明が最初に一致した項目の状態を変えた、新しい列を返す。一致する項目が無ければ null。
 * 状態 `ToDoListState` の適用と、射影 `projectFrom` が同じ規則を使う。
 */
fun List<ToDoItem>.withStatusChanged(description: String, newStatus: ToDoStatus): List<ToDoItem>? {
    val target = indexOfFirst { it.description == description }
    if (target < 0) return null
    return mapIndexed { index, item -> if (index == target) item.copy(status = newStatus) else item }
}
