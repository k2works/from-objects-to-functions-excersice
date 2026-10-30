package zettai.domain

@JvmInline
value class User(val name: String)

@JvmInline
value class ListName(val name: String)

@JvmInline
value class ToDoItem(val description: String)

data class ToDoList(
    val listName: ListName,
    val items: List<ToDoItem>,
)

fun findToDoList(
    lists: Map<User, List<ToDoList>>,
    user: User,
    listName: ListName,
): ToDoList? = lists[user]?.firstOrNull { it.listName == listName }
