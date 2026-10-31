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
import zettai.domain.StreamId
import zettai.domain.ToDoItem
import zettai.domain.ToDoStatus
import zettai.domain.User
import zettai.domain.errors.ConcurrentModification
import zettai.domain.errors.InvalidTransition
import zettai.domain.errors.ItemNotFound
import zettai.domain.errors.ListAlreadyExists
import zettai.domain.errors.ListNotFound
import zettai.domain.errors.PersistenceFailure
import zettai.domain.errors.ZettaiError
import zettai.domain.events.ItemAdded
import zettai.domain.events.ListCreated
import zettai.domain.events.ToDoListEvent

class ZettaiHttpAppTest : StringSpec({
    val request = Request(GET, "/todo/uberto/book")
    val user = User("uberto")
    val listName = ListName("book")

    fun appReading(events: List<ToDoListEvent>, requested: MutableList<Pair<User, ListName>> = mutableListOf()) =
        zettaiHttpApp(
            todoListHub(
                readAggregate = { u, l ->
                    requested += u to l
                    events.right()
                },
                readUser = { error("GET /todo/{user}/{list} は user 単位で読まない") },
                persist = { error("GET は永続化しない") },
            ),
        )

    "成功を HTML response に写像する" {
        val requested = mutableListOf<Pair<User, ListName>>()
        val app = appReading(
            listOf(ListCreated(user, listName), ItemAdded(user, listName, ToDoItem("write <chapter>"))),
            requested,
        )

        val response = app(request)

        requested shouldBe listOf(user to listName)
        response.status shouldBe Status.OK
        response.header("Content-Type") shouldBe "text/html; charset=utf-8"
        response.bodyString() shouldContain "write &lt;chapter&gt;"
    }

    "存在しないリストは 404 の plain text になる" {
        val response = appReading(emptyList())(request)

        response.status shouldBe Status.NOT_FOUND
        response.header("Content-Type") shouldBe "text/plain; charset=utf-8"
        response.bodyString() shouldBe ListNotFound(user, listName).message
    }

    // InvalidTransition と ListAlreadyExists は command の失敗である。
    // command route を接続する前に、error の変換表そのものを固定する。
    listOf<Pair<ZettaiError, Status>>(
        ListNotFound(user, listName) to Status.NOT_FOUND,
        ItemNotFound(user, listName, "write chapter") to Status.NOT_FOUND,
        ListAlreadyExists(user, listName) to Status.BAD_REQUEST,
        InvalidTransition("write chapter", ToDoStatus.Done, ToDoStatus.InProgress) to Status.BAD_REQUEST,
        PersistenceFailure("connection refused") to Status.INTERNAL_SERVER_ERROR,
        ConcurrentModification(listOf(StreamId(user, listName))) to Status.CONFLICT,
    ).forEach { (error, status) ->
        "${error::class.simpleName} を ${status.code} の plain text に写像する" {
            val response = toResponse(error)

            response.status shouldBe status
            response.header("Content-Type") shouldBe "text/plain; charset=utf-8"
            response.header("X-Content-Type-Options") shouldBe "nosniff"
            response.bodyString() shouldBe error.message
        }
    }

    "読み出しの失敗は app 経由でも 500 になり、原因を漏らさない" {
        val app = zettaiHttpApp(
            todoListHub(
                readAggregate = { _, _ ->
                    PersistenceFailure("jdbc:postgresql://db.internal/zettai password=secret").left()
                },
                readUser = { error("使わない") },
                persist = { error("使わない") },
            ),
        )

        val response = app(request)

        response.status shouldBe Status.INTERNAL_SERVER_ERROR
        response.header("Content-Type") shouldBe "text/plain; charset=utf-8"
        response.bodyString() shouldBe "Persistence failure"
    }

    "PersistenceFailure の原因は response に含めない" {
        val response = toResponse(PersistenceFailure("jdbc:postgresql://db.internal/zettai password=secret"))

        response.bodyString() shouldBe "Persistence failure"
    }
})
