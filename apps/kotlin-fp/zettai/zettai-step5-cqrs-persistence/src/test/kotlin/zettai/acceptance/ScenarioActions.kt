package zettai.acceptance

import arrow.core.getOrElse
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import zettai.adapter.inmemory.inMemoryEventStore
import zettai.application.ToDoListHub
import zettai.application.todoListHub
import zettai.domain.ToDoItem
import zettai.domain.ToDoList
import zettai.domain.User
import zettai.domain.commands.AddToDoItem
import zettai.domain.commands.CreateToDoList
import zettai.web.zettaiHttpApp

/** 前提の用意は command で行う。どの経路でも event store が唯一の源になる。 */
private suspend fun ToDoListHub.setUpWithCommands(user: User, list: ToDoList) {
    execute(CreateToDoList(user, list.listName)).getOrElse { error("前提の作成に失敗: ${it.message}") }
    list.items.forEach { item ->
        execute(AddToDoItem(user, list.listName, item)).getOrElse { error("前提の追加に失敗: ${it.message}") }
    }
}

fun domainActions(hub: ToDoListHub = freshHub()): ZettaiActions =
    ZettaiActions(
        name = "domain",
        setUp = hub::setUpWithCommands,
        getToDoList = { user, listName -> hub.getList(user, listName).getOrNull()?.items },
    )

fun httpActions(hub: ToDoListHub = freshHub()): ZettaiActions {
    val app = zettaiHttpApp(hub)
    return ZettaiActions(
        name = "HTTP",
        setUp = hub::setUpWithCommands,
        getToDoList = { user, listName ->
            val response = app(Request(GET, "/todo/${user.name}/${listName.name}"))
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

private fun freshHub(): ToDoListHub {
    val store = inMemoryEventStore()
    return todoListHub(store.readAggregate, store.readUser, store.persist)
}

private fun String.decodeHtmlEntities(): String = this
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&amp;", "&")
