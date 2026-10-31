package zettai.adapter.logging

import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import zettai.adapter.inmemory.inMemoryEventStore
import zettai.application.todoListHub
import zettai.arbCommand
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.User
import zettai.domain.commands.AddToDoItem
import zettai.domain.commands.CreateToDoList
import zettai.domain.errors.PersistenceFailure

private val start: Instant = Instant.parse("2026-11-01T00:00:00Z")

/** 呼ばれるたびに 25 ミリ秒進む時計。1 回の操作は、開始と終了で 2 回読むので、所要時間は 25 になる。 */
private fun steppingClock(): () -> Instant {
    var ticks = 0L
    return { start.plusMillis(25 * ticks++) }
}

private class Sink {
    val lines = ConcurrentLinkedQueue<String>()
    val write: (String) -> Unit = { lines.add(it) }
    fun records(): List<LogRecord> = lines.map { line -> logLine.decoder.run(line).getOrElse { error("読めない行: $line") } }
}

class LoggedHubTest : StringSpec({
    val alice = User("alice")
    val book = ListName("book")

    "command の成功が、1 行の構造化ログになる" {
        val sink = Sink()
        val hub = todoListHub(inMemoryEventStore()).logged(sink.write, steppingClock())

        hub.execute(CreateToDoList(alice, book))

        sink.records() shouldBe listOf(
            LogRecord(
                at = start,
                kind = "command",
                name = "CreateToDoList",
                outcome = "success",
                user = "alice",
                list = "book",
                durationMs = 25,
                events = 1,
            ),
        )
    }

    "command の拒否も 1 行で出る。結果の型が載る" {
        val sink = Sink()
        val hub = todoListHub(inMemoryEventStore()).logged(sink.write, steppingClock())

        hub.execute(AddToDoItem(alice, book, ToDoItem("write")))

        sink.records().single().outcome shouldBe "ListNotFound"
        sink.records().single().events shouldBe null
    }

    "query も 1 行で出る" {
        val sink = Sink()
        val hub = todoListHub(inMemoryEventStore()).logged(sink.write, steppingClock())

        hub.getList(alice, book)
        hub.listRows(alice)

        sink.records().map { it.kind to it.name } shouldBe listOf("query" to "getList", "query" to "listRows")
        sink.records().map { it.outcome } shouldBe listOf("ListNotFound", "success")
    }

    "項目の説明は、ログに載らない" {
        val sink = Sink()
        val hub = todoListHub(inMemoryEventStore()).logged(sink.write, steppingClock())

        hub.execute(CreateToDoList(alice, book))
        hub.execute(AddToDoItem(alice, book, ToDoItem("secret-token-12345")))

        sink.lines.forEach { it shouldNotContain "secret-token-12345" }
        sink.lines shouldHaveSize 2
    }

    "永続化の失敗の原因は、ログに載らない" {
        val sink = Sink()
        val store = inMemoryEventStore().copy(
            readAggregate = { _, _ -> PersistenceFailure("jdbc:postgresql://db.internal:5432 password=secret").left() },
        )
        val hub = todoListHub(store).logged(sink.write, steppingClock())

        hub.getList(alice, book)

        sink.lines.single().let {
            it shouldNotContain "secret"
            it shouldNotContain "db.internal"
        }
        sink.records().single().outcome shouldBe "PersistenceFailure"
    }

    "利用者名とリスト名に特殊文字があっても、ログは改行を含まない 1 行の JSON のまま、同じ値に読み戻せる" {
        val nasty = "say \"hi\" \\ back\nline 日本語 🚀"
        val sink = Sink()
        val hub = todoListHub(inMemoryEventStore()).logged(sink.write, steppingClock())

        hub.execute(CreateToDoList(User(nasty), ListName(nasty)))

        sink.lines.single().contains('\n') shouldBe false
        sink.records().single().user shouldBe nasty
        sink.records().single().list shouldBe nasty
    }

    "decorator を付けても、任意の command 列に対する結果は変わらない" {
        checkAll(25, Arb.list(arbCommand, 0..15)) { commands ->
            val plain = todoListHub(inMemoryEventStore())
            val logged = todoListHub(inMemoryEventStore()).logged(Sink().write, steppingClock())

            val expected = commands.map { plain.execute(it) }
            val actual = commands.map { logged.execute(it) }

            actual shouldBe expected
        }
    }

    "並行する command でも、1 回の操作は 1 回の書き込みで、行が壊れない" {
        val sink = Sink()
        val hub = todoListHub(inMemoryEventStore()).logged(sink.write, steppingClock())

        coroutineScope {
            (1..40).map { n ->
                async(Dispatchers.Default) { hub.execute(CreateToDoList(User("user$n"), ListName("list$n"))) }
            }.awaitAll()
        }

        sink.lines shouldHaveSize 40
        sink.records().map { it.user }.toSet() shouldBe (1..40).map { "user$it" }.toSet()
    }

    "拒否されても成功しても、操作の戻り値をそのまま返す" {
        val hub = todoListHub(inMemoryEventStore()).logged(Sink().write, steppingClock())

        hub.execute(CreateToDoList(alice, book)).isRight() shouldBe true
        hub.execute(CreateToDoList(alice, book)).isLeft() shouldBe true
        hub.getList(alice, book).map { it.items } shouldBe emptyList<ToDoItem>().right()
    }
})
