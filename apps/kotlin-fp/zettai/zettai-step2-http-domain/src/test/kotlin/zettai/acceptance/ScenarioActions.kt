package zettai.acceptance

import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import zettai.application.inMemoryFetcher
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoList
import zettai.domain.User
import zettai.domain.findToDoList
import zettai.web.zettaiHttpApp

fun domainActions(): ZettaiActions {
    val lists = mutableMapOf<User, List<ToDoList>>()
    return ZettaiActions(
        name = "domain",
        setUp = lists::add,
        getToDoList = { user, listName -> findToDoList(lists, user, listName)?.items },
    )
}

fun httpActions(): ZettaiActions {
    val lists = mutableMapOf<User, List<ToDoList>>()
    return ZettaiActions(
        name = "HTTP",
        setUp = lists::add,
        getToDoList = { user, listName ->
            val response = zettaiHttpApp(inMemoryFetcher(lists))(
                Request(GET, "/todo/${user.name}/${listName.name}"),
            )
            if (response.status != Status.OK) {
                null
            } else {
                "<li>(.*?)</li>".toRegex()
                    .findAll(response.bodyString())
                    .map { ToDoItem(it.groupValues[1].decodeHtmlEntities()) }
                    .toList()
            }
        },
    )
}

private fun MutableMap<User, List<ToDoList>>.add(user: User, list: ToDoList) {
    this[user] = getOrDefault(user, emptyList()) + list
}

private fun String.decodeHtmlEntities(): String = this
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&amp;", "&")
