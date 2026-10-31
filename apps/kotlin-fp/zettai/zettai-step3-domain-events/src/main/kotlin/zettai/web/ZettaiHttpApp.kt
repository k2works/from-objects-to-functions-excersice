package zettai.web

import kotlinx.coroutines.runBlocking
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import zettai.application.ToDoListHub
import zettai.domain.ListName
import zettai.domain.User

fun zettaiHttpApp(hub: ToDoListHub): HttpHandler = routes(
    "/todo/{user}/{list}" bind GET to { request ->
        val user = User(request.path("user").orEmpty())
        val listName = ListName(request.path("list").orEmpty())
        val todoList = runBlocking { hub.getList(user, listName) }

        todoList
            ?.let {
                Response(Status.OK)
                    .header("Content-Type", "text/html; charset=utf-8")
                    .body(renderHtml(it))
            }
            ?: Response(Status.NOT_FOUND)
    },
)
