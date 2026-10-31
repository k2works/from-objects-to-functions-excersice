package zettai.app

import arrow.core.getOrElse
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.runBlocking
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import zettai.adapter.inmemory.inMemoryEventStore
import zettai.application.todoListHub
import zettai.domain.ListName
import zettai.domain.User
import zettai.domain.commands.CreateToDoList
import zettai.logging.LogRecord
import zettai.logging.logLine

private fun clock(): () -> Instant {
    var ticks = 0L
    return { Instant.parse("2026-11-01T00:00:00Z").plusMillis(10 * ticks++) }
}

class ObservedAppTest : StringSpec({
    fun records(lines: Collection<String>): List<LogRecord> =
        lines.map { line -> logLine.decoder.run(line).getOrElse { error("読めない行: $line") } }

    "1 つの request で、store、query、request の行が、内側から外側の順に出る" {
        val lines = ConcurrentLinkedQueue<String>()
        val store = inMemoryEventStore()
        runBlocking { todoListHub(store).execute(CreateToDoList(User("alice"), ListName("book"))) }
        val app = observedApp(store, { lines.add(it) }, clock())

        val response = app(Request(GET, "/todo/alice/book"))

        response.status shouldBe Status.OK
        records(lines).map { it.kind to it.name } shouldBe listOf(
            "store" to "readAggregate",
            "query" to "getList",
            "request" to "GET",
        )
    }

    "組み立てた app は、予期しない例外を 500 として request の行に残し、原因を載せない" {
        val lines = ConcurrentLinkedQueue<String>()
        val store = inMemoryEventStore().copy(
            readAggregate = { _, _ -> error("jdbc:postgresql://db.internal password=secret") },
        )
        val app = observedApp(store, { lines.add(it) }, clock())

        val response = app(Request(GET, "/todo/alice/book"))

        response.status shouldBe Status.INTERNAL_SERVER_ERROR
        records(lines).last().let {
            it.kind shouldBe "request"
            it.status shouldBe 500
        }
        lines.forEach {
            it shouldNotContain "secret"
            it shouldNotContain "db.internal"
        }
    }
})
