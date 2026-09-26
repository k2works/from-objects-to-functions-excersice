package zettai.web

import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import zettai.domain.ListName
import zettai.domain.ToDoListHub
import zettai.domain.User

/**
 * Zettai のウェブアプリケーション。
 *
 * HttpHandler は (Request) -> Response の関数なので、Zettai 自身が関数として振る舞う。
 * リストの取り出しはハブに委ねる。HTTP の層はドメインの呼び出し方を知らない。
 */
class Zettai(private val hub: ToDoListHub) : HttpHandler {

    private val routes = routes(
        "/todo/{user}/{list}" bind Method.GET to ::showList
    )

    override fun invoke(request: Request): Response = routes(request)

    private fun showList(request: Request): Response {
        val user = User(request.path("user").orEmpty())
        val listName = ListName(request.path("list").orEmpty())

        val todoList = hub.getList(user, listName) ?: return Response(Status.NOT_FOUND)

        return Response(Status.OK).body(renderHtml(todoList))
    }
}
