package zettai.web

import kotlinx.coroutines.runBlocking
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.core.then
import org.http4k.filter.ServerFilters
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import zettai.application.ToDoListHub
import zettai.domain.ListName
import zettai.domain.User
import zettai.domain.errors.ConcurrentModification
import zettai.domain.errors.InvalidInput
import zettai.domain.errors.InvalidTransition
import zettai.domain.errors.ItemNotFound
import zettai.domain.errors.ListAlreadyExists
import zettai.domain.errors.ListNotFound
import zettai.domain.errors.PersistenceFailure
import zettai.domain.errors.ZettaiError
import zettai.domain.validation.validateRename

fun zettaiHttpApp(hub: ToDoListHub): HttpHandler =
    ServerFilters.CatchAll(::unexpectedFailure).then(
        routes(
            "/todo/{user}/{list}" bind GET to { request ->
                showList(request, hub)
            },
            "/todo/{user}/{list}/rename" bind GET to { request ->
                showRenameForm(request, hub)
            },
            "/todo/{user}/{list}/rename" bind POST to { request ->
                rename(request, hub)
            },
        ),
    )

private fun showList(request: Request, hub: ToDoListHub): Response =
    runBlocking {
        val user = User(request.path("user").orEmpty())
        val listName = ListName(request.path("list").orEmpty())

        hub.getList(user, listName).fold(
            ifLeft = ::toResponse,
            ifRight = { html(Status.OK, renderHtml(user, it)) },
        )
    }

private fun html(status: Status, body: String): Response =
    Response(status).header("Content-Type", "text/html; charset=utf-8").body(body)

private fun showRenameForm(request: Request, hub: ToDoListHub): Response =
    runBlocking {
        val user = User(request.path("user").orEmpty())
        val listName = ListName(request.path("list").orEmpty())

        hub.getList(user, listName).fold(
            ifLeft = ::toResponse,
            ifRight = { html(Status.OK, renderRenameForm(user, listName, listName.name, emptyList())) },
        )
    }

/** 検証の誤りは 400 で、入力を保持した画面に並べる。成功したら新しい名前の画面へ 302 で移る（PRG）。 */
private fun rename(request: Request, hub: ToDoListHub): Response =
    runBlocking {
        val rawUser = request.path("user").orEmpty()
        val rawList = request.path("list").orEmpty()
        val rawNewName = request.form("newName").orEmpty()

        validateRename(rawUser, rawList, rawNewName).fold(
            ifLeft = { invalid ->
                html(
                    Status.BAD_REQUEST,
                    renderRenameForm(
                        User(rawUser),
                        ListName(rawList),
                        rawNewName,
                        invalid.problems.map(::describeProblem),
                    ),
                )
            },
            ifRight = { command ->
                hub.execute(command).fold(
                    ifLeft = { error ->
                        if (error is ListAlreadyExists || error is ListNotFound || error is ConcurrentModification) {
                            html(
                                statusOf(error),
                                renderRenameForm(
                                    command.user,
                                    command.listName,
                                    rawNewName,
                                    listOf(describeError(error)),
                                ),
                            )
                        } else {
                            toResponse(error)
                        }
                    },
                    ifRight = {
                        Response(Status.FOUND).header("Location", listPath(command.user, command.newName))
                    },
                )
            },
        )
    }

/** 予期しない例外の詳細（接続先、スタックトレース）は本文に出さない。固定の本文だけを返す。 */
private fun unexpectedFailure(@Suppress("UNUSED_PARAMETER") failure: Throwable): Response =
    Response(Status.INTERNAL_SERVER_ERROR)
        .header("Content-Type", "text/plain; charset=utf-8")
        .header("X-Content-Type-Options", "nosniff")
        .body("Internal server error")

internal fun toResponse(error: ZettaiError): Response =
    Response(statusOf(error))
        .header("Content-Type", "text/plain; charset=utf-8")
        .header("X-Content-Type-Options", "nosniff")
        .body(error.message)

private fun statusOf(error: ZettaiError): Status =
    when (error) {
        is ListNotFound,
        is ItemNotFound,
        -> Status.NOT_FOUND

        is ListAlreadyExists,
        is InvalidInput,
        is InvalidTransition,
        -> Status.BAD_REQUEST

        is ConcurrentModification -> Status.CONFLICT

        is PersistenceFailure -> Status.INTERNAL_SERVER_ERROR
    }
