package zettai.web

import arrow.core.getOrElse
import arrow.core.left
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.runBlocking
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.then
import zettai.adapter.inmemory.inMemoryEventStore
import zettai.application.ToDoListHub
import zettai.application.todoListHub
import zettai.domain.ListName
import zettai.domain.StreamId
import zettai.domain.User
import zettai.domain.commands.CreateToDoList
import zettai.domain.errors.ConcurrentModification
import zettai.logging.LogRecord
import zettai.logging.logLine

private val begin: Instant = Instant.parse("2026-11-01T00:00:00Z")

private fun ticking(): () -> Instant {
    var ticks = 0L
    return { begin.plusMillis(40 * ticks++) }
}

class RequestLogTest : StringSpec({
    val alice = User("alice")
    val book = ListName("book")

    fun app(hub: ToDoListHub, lines: ConcurrentLinkedQueue<String>): HttpHandler =
        requestLogged({ lines.add(it) }, ticking()).then(zettaiHttpApp(hub))

    fun hubWithBook(): ToDoListHub {
        val hub = todoListHub(inMemoryEventStore())
        runBlocking { hub.execute(CreateToDoList(alice, book)) }
        return hub
    }

    fun records(lines: Collection<String>): List<LogRecord> =
        lines.map { line -> logLine.decoder.run(line).getOrElse { error("読めない行: $line") } }

    fun form(body: String) =
        Request(POST, "/todo/alice/book/rename")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body(body)

    "成功した request が、method、path、status、所要時間とともに 1 行で出る" {
        val lines = ConcurrentLinkedQueue<String>()

        app(hubWithBook(), lines)(Request(GET, "/todo/alice/book"))

        records(lines) shouldBe listOf(
            LogRecord(
                at = begin,
                kind = "request",
                name = "GET",
                outcome = "success",
                path = "/todo/alice/book",
                status = 200,
                durationMs = 40,
            ),
        )
    }

    "404、400、409、500 も、status とともに記録される" {
        val lines = ConcurrentLinkedQueue<String>()
        val conflict = ConcurrentModification(listOf(StreamId(alice, book)))
        val store = inMemoryEventStore()
        val conflicting = todoListHub(store.copy(append = { _, _, _ -> conflict.left() }))
        runBlocking { todoListHub(store).execute(CreateToDoList(alice, book)) }
        val broken = ToDoListHub(
            getList = { _, _ -> error("boom password=secret") },
            listRows = { error("使わない") },
            execute = { error("使わない") },
        )

        app(hubWithBook(), lines)(Request(GET, "/todo/alice/missing"))
        app(hubWithBook(), lines)(form("newName="))
        app(conflicting, lines)(form("newName=novel"))
        app(broken, lines)(Request(GET, "/todo/alice/book"))

        records(lines).map { it.status to it.outcome } shouldBe listOf(
            404 to "clientError",
            400 to "clientError",
            409 to "clientError",
            500 to "serverError",
        )
        lines.forEach { it shouldNotContain "secret" }
    }

    "query と body は、ログに載らない" {
        val lines = ConcurrentLinkedQueue<String>()

        val response = app(hubWithBook(), lines)(
            Request(GET, "/todo/alice/book?token=secret-token-12345"),
        )
        app(hubWithBook(), lines)(form("newName=secret-newname"))

        response.status shouldBe Status.OK
        lines.forEach {
            it shouldNotContain "secret-token-12345"
            it shouldNotContain "secret-newname"
        }
        records(lines).map { it.path } shouldBe listOf("/todo/alice/book", "/todo/alice/book/rename")
    }

    "path に特殊文字があっても、ログは改行を含まない 1 行の JSON のまま読み戻せる" {
        val lines = ConcurrentLinkedQueue<String>()

        app(hubWithBook(), lines)(Request(GET, "/todo/alice/%E6%97%A5%E6%9C%AC%22%5C%0A"))

        lines.single().contains('\n') shouldBe false
        records(lines).single().status shouldBe 404
    }

    "filter を付けても、response は変わらない" {
        val plain = zettaiHttpApp(hubWithBook())(Request(GET, "/todo/alice/book"))
        val logged = app(hubWithBook(), ConcurrentLinkedQueue())(Request(GET, "/todo/alice/book"))

        logged.status shouldBe plain.status
        logged.bodyString() shouldBe plain.bodyString()
    }

    "header は、ログに載らない" {
        val lines = ConcurrentLinkedQueue<String>()

        app(hubWithBook(), lines)(
            Request(GET, "/todo/alice/book").header("Authorization", "Bearer secret-token-12345"),
        )

        lines.forEach { it shouldNotContain "secret-token-12345" }
    }
})
