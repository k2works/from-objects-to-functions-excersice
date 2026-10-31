package zettai.acceptance

import arrow.core.Either
import arrow.core.getOrElse
import kotlinx.coroutines.runBlocking
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import zettai.adapter.inmemory.inMemoryEventStore
import zettai.adapter.postgres.SharedPostgres
import zettai.adapter.postgres.postgresEventStore
import zettai.application.ToDoListHub
import zettai.application.todoListHub
import zettai.domain.ToDoItem
import zettai.domain.ToDoList
import zettai.domain.User
import zettai.domain.commands.AddToDoItem
import zettai.domain.commands.CreateToDoList
import zettai.domain.errors.ListNotFound
import zettai.domain.errors.ZettaiError
import zettai.web.zettaiHttpApp

/** 前提の用意は command で行う。どの経路でも event store が唯一の源になる。 */
private suspend fun ToDoListHub.setUpWithCommands(user: User, list: ToDoList) {
    execute(CreateToDoList(user, list.listName)).getOrElse { error("前提の作成に失敗: ${it.message}") }
    list.items.forEach { item ->
        execute(AddToDoItem(user, list.listName, item)).getOrElse { error("前提の追加に失敗: ${it.message}") }
    }
}

/** 見つからないことは null にする。永続化の失敗は「見つからない」に紛れさせず、テストを失敗させる。 */
private fun Either<ZettaiError, ToDoList>.itemsOrNull(): List<ToDoItem>? =
    fold(
        ifLeft = { failure -> if (failure is ListNotFound) null else error("取得に失敗: ${failure.message}") },
        ifRight = { it.items },
    )

fun domainActions(hub: ToDoListHub = freshHub()): ZettaiActions =
    ZettaiActions(
        name = "domain",
        setUp = hub::setUpWithCommands,
        getToDoList = { user, listName -> hub.getList(user, listName).itemsOrNull() },
    )

/**
 * PostgreSQL 経由。前提を書き込む Hub と、読み出す Hub を別々に組み立てる。
 * 読み出しのたびに組み直すので、再起動しても残っていることを確かめる経路になる。
 * scenario ごとにテーブルを作り直し、他の scenario の状態に依存しない。
 */
fun postgresActions(): ZettaiActions {
    runBlocking { SharedPostgres.reset() }
    fun newHub(): ToDoListHub = todoListHub(postgresEventStore(SharedPostgres::connect))
    val writer = newHub()
    return ZettaiActions(
        name = "PostgreSQL",
        setUp = writer::setUpWithCommands,
        getToDoList = { user, listName -> newHub().getList(user, listName).itemsOrNull() },
    )
}

fun httpActions(hub: ToDoListHub = freshHub()): ZettaiActions {
    val app = zettaiHttpApp(hub)
    return ZettaiActions(
        name = "HTTP",
        setUp = hub::setUpWithCommands,
        getToDoList = { user, listName ->
            val response = app(Request(GET, "/todo/${user.name}/${listName.name}"))
            when (response.status) {
                Status.OK ->
                    "<li>(.*?)</li>".toRegex()
                        .findAll(response.bodyString())
                        .map { ToDoItem(it.groupValues[1].decodeHtmlEntities()) }
                        .toList()
                // 500 などを「見つからない」に紛れさせない
                Status.NOT_FOUND -> null
                else -> error("HTTP ${response.status}: ${response.bodyString()}")
            }
        },
    )
}

private fun freshHub(): ToDoListHub = todoListHub(inMemoryEventStore())

private fun String.decodeHtmlEntities(): String = this
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&amp;", "&")
