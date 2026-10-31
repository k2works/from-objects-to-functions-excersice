package zettai.web

import kotlinx.coroutines.runBlocking
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.filter.ServerFilters
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import zettai.application.ToDoListHub
import zettai.domain.ListName
import zettai.domain.User
import zettai.domain.errors.InvalidTransition
import zettai.domain.errors.ItemNotFound
import zettai.domain.errors.ListAlreadyExists
import zettai.domain.errors.ListNotFound
import zettai.domain.errors.ZettaiError

fun zettaiHttpApp(hub: ToDoListHub): HttpHandler =
    ServerFilters.CatchAll().then(
        routes(
            "/todo/{user}/{list}" bind GET to { request ->
                showList(request, hub)
            },
        ),
    )

private fun showList(request: Request, hub: ToDoListHub): Response =
    runBlocking {
        val user = User(request.path("user").orEmpty())
        val listName = ListName(request.path("list").orEmpty())

        hub.getList(user, listName).fold(
            ifLeft = ::toResponse,
            ifRight = {
                Response(Status.OK)
                    .header("Content-Type", "text/html; charset=utf-8")
                    .body(renderHtml(it))
            },
        )
    }

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
        is InvalidTransition,
        -> Status.BAD_REQUEST
    }
