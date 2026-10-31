package zettai.acceptance

import arrow.core.Either
import arrow.core.getOrElse
import java.net.URLEncoder
import kotlinx.coroutines.runBlocking
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import zettai.adapter.inmemory.inMemoryEventStore
import zettai.adapter.postgres.SharedPostgres
import zettai.adapter.postgres.postgresEventStore
import zettai.application.ToDoListHub
import zettai.application.todoListHub
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoList
import zettai.domain.User
import zettai.domain.commands.AddToDoItem
import zettai.domain.commands.CreateToDoList
import zettai.domain.errors.InvalidInput
import zettai.domain.errors.ListNotFound
import zettai.domain.errors.ZettaiError
import zettai.domain.validation.validateRename
import zettai.web.describeError
import zettai.web.describeProblem
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

/** domain と PostgreSQL の経路。検証してから command を実行する。 */
private suspend fun ToDoListHub.renameWithCommands(user: User, listName: ListName, rawNewName: String): RenameOutcome =
    validateRename(user.name, listName.name, rawNewName).fold(
        ifLeft = { invalid -> RenameOutcome.Rejected(invalid.problems.map(::describeProblem)) },
        ifRight = { command ->
            execute(command).fold(
                ifLeft = { RenameOutcome.Rejected(listOf(describeError(it))) },
                ifRight = { RenameOutcome.Renamed },
            )
        },
    )

fun domainActions(hub: ToDoListHub = freshHub()): ZettaiActions =
    ZettaiActions(
        name = "domain",
        setUp = hub::setUpWithCommands,
        getToDoList = { user, listName -> hub.getList(user, listName).itemsOrNull() },
        rename = hub::renameWithCommands,
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
        rename = { user, listName, newName -> newHub().renameWithCommands(user, listName, newName) },
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
        rename = { user, listName, newName ->
            val response = app(
                Request(POST, "/todo/${user.name}/${listName.name}/rename")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("newName=" + URLEncoder.encode(newName, Charsets.UTF_8)),
            )
            when (response.status) {
                Status.FOUND -> RenameOutcome.Renamed
                Status.BAD_REQUEST, Status.NOT_FOUND -> RenameOutcome.Rejected(response.reasons())
                else -> error("HTTP ${response.status}: ${response.bodyString()}")
            }
        },
    )
}

/** 検証の誤りは画面の `<li class="problem">`、それ以外は本文そのものが理由になる。 */
private fun Response.reasons(): List<String> {
    val problems = "<li class=\"problem\">(.*?)</li>".toRegex()
        .findAll(bodyString())
        .map { it.groupValues[1] }
        .toList()
    return (problems.ifEmpty { listOf(bodyString()) }).map { it.decodeHtmlEntities() }
}

private fun freshHub(): ToDoListHub = todoListHub(inMemoryEventStore())

private fun String.decodeHtmlEntities(): String = this
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&amp;", "&")
