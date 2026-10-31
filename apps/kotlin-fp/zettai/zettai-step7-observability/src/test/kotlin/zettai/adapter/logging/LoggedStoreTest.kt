package zettai.adapter.logging

import arrow.core.getOrElse
import arrow.core.left
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import zettai.adapter.inmemory.inMemoryEventStore
import zettai.application.EventStore
import zettai.application.todoListHub
import zettai.domain.ListName
import zettai.domain.StreamId
import zettai.domain.ToDoItem
import zettai.domain.User
import zettai.domain.commands.AddToDoItem
import zettai.domain.commands.CreateToDoList
import zettai.domain.commands.RenameToDoList
import zettai.domain.errors.ConcurrentModification
import zettai.domain.events.ItemAdded
import zettai.domain.events.ListCreated
import zettai.logging.LogRecord
import zettai.logging.logLine

private val epoch: Instant = Instant.parse("2026-11-01T00:00:00Z")

private fun clock(): () -> Instant {
    var ticks = 0L
    return { epoch.plusMillis(10 * ticks++) }
}

class LoggedStoreTest : StringSpec({
    val alice = User("alice")
    val book = ListName("book")
    val novel = ListName("novel")

    fun logs(): Pair<ConcurrentLinkedQueue<String>, (String) -> Unit> {
        val lines = ConcurrentLinkedQueue<String>()
        return lines to { lines.add(it) }
    }

    fun records(lines: Collection<String>): List<LogRecord> =
        lines.map { line -> logLine.decoder.run(line).getOrElse { error("読めない行: $line") } }

    "追記と読み出しが、stream の数、event の件数、所要時間とともに記録される" {
        val (lines, write) = logs()
        val store: EventStore<Unit> = inMemoryEventStore().logged(write, clock())
        val hub = todoListHub(store)

        hub.execute(CreateToDoList(alice, book))

        records(lines).map { Triple(it.kind, it.name, it.outcome) } shouldBe listOf(
            Triple("store", "readStream", "success"),
            Triple("store", "append", "success"),
        )
        records(lines).first { it.name == "readStream" }.let {
            it.user shouldBe "alice"
            it.list shouldBe "book"
            it.events shouldBe 0
            it.durationMs shouldBe 10
        }
        records(lines).first { it.name == "append" }.let {
            it.streams shouldBe 1
            it.events shouldBe 1
        }
    }

    "query 側の読み出しも記録される" {
        val (lines, write) = logs()
        val store = inMemoryEventStore().logged(write, clock())
        val hub = todoListHub(store)
        hub.execute(CreateToDoList(alice, book))
        lines.clear()

        hub.getList(alice, book)
        hub.listRows(alice)

        records(lines).map { it.name } shouldBe listOf("readAggregate", "readUser")
        records(lines).first { it.name == "readAggregate" }.events shouldBe 1
        records(lines).first { it.name == "readUser" }.list shouldBe null
    }

    "名前変更は、複数の stream への追記を 1 行で記録する" {
        val (lines, write) = logs()
        val store = inMemoryEventStore().logged(write, clock())
        val hub = todoListHub(store)
        hub.execute(CreateToDoList(alice, book))
        hub.execute(AddToDoItem(alice, book, ToDoItem("write")))
        lines.clear()

        hub.execute(RenameToDoList(alice, book, novel))

        val append = records(lines).single { it.name == "append" }
        append.streams shouldBe 2
        append.events shouldBe 3
        append.user shouldBe "alice"
        append.list shouldBe null
        records(lines).count { it.name == "readStream" } shouldBe 2
    }

    "追記の競合も記録され、結果の型が載る" {
        val (lines, write) = logs()
        val base = inMemoryEventStore()
        val conflicting = base.copy(
            append = { _, _, _ -> ConcurrentModification(listOf(StreamId(alice, book))).left() },
        )
        val hub = todoListHub(conflicting.logged(write, clock()))

        hub.execute(CreateToDoList(alice, book))

        records(lines).single { it.name == "append" }.outcome shouldBe "ConcurrentModification"
    }

    "payload は載らない" {
        val (lines, write) = logs()
        val store = inMemoryEventStore().logged(write, clock())
        val hub = todoListHub(store)

        hub.execute(CreateToDoList(alice, book))
        hub.execute(AddToDoItem(alice, book, ToDoItem("secret-token-12345")))

        lines.forEach { it shouldNotContain "secret-token-12345" }
        lines.size shouldBe 4
    }

    "decorator を付けても、store の戻り値は変わらない" {
        val (_, write) = logs()
        val logged = inMemoryEventStore().logged(write, clock())
        val events = listOf(ListCreated(alice, book), ItemAdded(alice, book, ToDoItem("a")))

        val appended = logged.transaction { tx ->
            logged.append(tx, mapOf(StreamId(alice, book) to 0), events).map { events }
        }

        appended.getOrElse { error(it.message) } shouldBe events
        logged.readAggregate(alice, book).getOrElse { error(it.message) } shouldBe events
        logged.readUser(alice).getOrElse { error(it.message) } shouldBe events
    }

    "stream が 1 つの追記は、その利用者とリスト名を載せる" {
        val (lines, write) = logs()
        val store = inMemoryEventStore().logged(write, clock())

        todoListHub(store).execute(CreateToDoList(alice, book))

        val append = records(lines).single { it.name == "append" }
        append.user shouldBe "alice"
        append.list shouldBe "book"
    }

    "stream が無い追記は、識別子を載せず、stream の数を 0 と記録する" {
        val (lines, write) = logs()
        val store = inMemoryEventStore().logged(write, clock())

        store.transaction { tx -> store.append(tx, emptyMap(), emptyList()).map { emptyList() } }

        val append = records(lines).single()
        append.streams shouldBe 0
        append.user shouldBe null
        append.list shouldBe null
    }

    "readUser は利用者と件数を記録する" {
        val (lines, write) = logs()
        val store = inMemoryEventStore().logged(write, clock())
        todoListHub(store).execute(CreateToDoList(alice, book))
        lines.clear()

        store.readUser(alice)

        records(lines).single().let {
            it.name shouldBe "readUser"
            it.user shouldBe "alice"
            it.events shouldBe 1
        }
    }
})
