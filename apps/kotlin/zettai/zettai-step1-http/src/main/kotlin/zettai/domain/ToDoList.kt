package zettai.domain

/**
 * ToDo リストのドメイン。
 *
 * このパッケージは HTTP を知らない。http4k の型を import しないことが、
 * ドメインとインフラストラクチャの境界そのものになる。
 */
data class User(val name: String)

data class ListName(val name: String)

data class ToDoItem(val description: String)

data class ToDoList(val listName: ListName, val items: List<ToDoItem>)
