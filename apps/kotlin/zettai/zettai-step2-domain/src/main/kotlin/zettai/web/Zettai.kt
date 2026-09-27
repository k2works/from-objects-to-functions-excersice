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
import zettai.fp.InvalidTransition
import zettai.fp.ItemNotFound
import zettai.fp.ListAlreadyExists
import zettai.fp.ListNotFound
import zettai.fp.ZettaiError

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

        return hub.getList(user, listName)
            .fold(::toResponse) { Response(Status.OK).body(renderHtml(it)) }
    }
}

/**
 * 失敗の種類に応じてステータスコードを決める。
 *
 * null だった頃は、すべての失敗が 404 だった。
 * 型で区別できるようになったので、利用者に伝える内容を変えられる。
 */
private fun toResponse(error: ZettaiError): Response =
    when (error) {
        is ListNotFound, is ItemNotFound -> Response(Status.NOT_FOUND).body(error.message)
        is ListAlreadyExists, is InvalidTransition -> Response(Status.BAD_REQUEST).body(error.message)
    }
