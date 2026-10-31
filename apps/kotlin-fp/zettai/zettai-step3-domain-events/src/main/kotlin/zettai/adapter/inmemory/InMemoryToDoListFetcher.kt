package zettai.adapter.inmemory

import zettai.application.ToDoListFetcher
import zettai.domain.ToDoList
import zettai.domain.User
import zettai.domain.findToDoList

fun inMemoryFetcher(lists: Map<User, List<ToDoList>>): ToDoListFetcher {
    val snapshot = lists
        .mapValues { (_, todoLists) ->
            todoLists.map { todoList ->
                todoList.copy(items = todoList.items.toList())
            }
        }
        .toMap()

    return { user, listName -> findToDoList(snapshot, user, listName) }
}
