package zettai.web

import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import zettai.application.todoListHub
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoList
import zettai.domain.ToDoStatus
import zettai.domain.User
import zettai.domain.errors.InvalidTransition
import zettai.domain.errors.ItemNotFound
import zettai.domain.errors.ListAlreadyExists
import zettai.domain.errors.ListNotFound
import zettai.domain.errors.ZettaiError
import zettai.domain.events.ToDoListState

class ZettaiHttpAppTest : StringSpec({
    val request = Request(GET, "/todo/uberto/book")
    val user = User("uberto")
    val listName = ListName("book")

    fun appReturning(error: ZettaiError) =
        zettaiHttpApp(
            todoListHub(
                fetchList = { _, _ -> error.left() },
                fetchState = { ToDoListState.empty },
                persist = {},
            ),
        )

    "成功を HTML response に写像する" {
        val requested = mutableListOf<Pair<User, ListName>>()
        val app = zettaiHttpApp(
            todoListHub(
                fetchList = { user, listName ->
                    requested += user to listName
                    ToDoList(listName, listOf(ToDoItem("write <chapter>"))).right()
                },
                fetchState = { ToDoListState.empty },
                persist = {},
            ),
        )
        val response = app(request)

        requested shouldBe listOf(user to listName)
        response.status shouldBe Status.OK
        response.header("Content-Type") shouldBe "text/html; charset=utf-8"
        response.bodyString() shouldContain "write &lt;chapter&gt;"
    }

    // InvalidTransition と ListAlreadyExists は command の失敗である。
    // command route を接続する前に、変換表だけを fetcher への注入で固定する。
    listOf(
        ListNotFound(user, listName) to Status.NOT_FOUND,
        ItemNotFound(user, listName, "write chapter") to Status.NOT_FOUND,
        ListAlreadyExists(user, listName) to Status.BAD_REQUEST,
        InvalidTransition("write chapter", ToDoStatus.Done, ToDoStatus.InProgress) to Status.BAD_REQUEST,
    ).forEach { (error, status) ->
        "${error::class.simpleName} を ${status.code} の plain text に写像する" {
            val response = appReturning(error)(request)

            response.status shouldBe status
            response.header("Content-Type") shouldBe "text/plain; charset=utf-8"
            response.header("X-Content-Type-Options") shouldBe "nosniff"
            response.bodyString() shouldBe error.message
        }
    }
})
