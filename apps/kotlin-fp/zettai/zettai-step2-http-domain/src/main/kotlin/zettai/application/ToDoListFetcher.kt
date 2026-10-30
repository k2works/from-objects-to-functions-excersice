package zettai.application

import zettai.domain.ListName
import zettai.domain.ToDoList
import zettai.domain.User
import zettai.domain.findToDoList

typealias ToDoListFetcher = suspend (User, ListName) -> ToDoList?

fun inMemoryFetcher(lists: Map<User, List<ToDoList>>): ToDoListFetcher =
    { user, listName -> findToDoList(lists, user, listName) }
