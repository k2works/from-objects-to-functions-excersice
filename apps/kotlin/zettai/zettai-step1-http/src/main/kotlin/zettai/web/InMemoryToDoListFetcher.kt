package zettai.web

import zettai.domain.ListName
import zettai.domain.ToDoList
import zettai.domain.User

/**
 * インメモリの Map から ToDo リストを取り出す。
 *
 * 第 9 章で永続化に置き換える。それまでは、アプリケーションを動かすのに
 * データベースを用意しなくてよいことのほうが価値が大きい。
 */
fun inMemoryFetcher(lists: Map<User, List<ToDoList>>): ToDoListFetcher =
    { user, listName -> lists[user]?.firstOrNull { it.listName == listName } }
