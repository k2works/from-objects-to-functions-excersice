package zettai.application

import arrow.core.getOrElse
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import zettai.domain.ListName
import zettai.domain.StreamId
import zettai.domain.ToDoItem
import zettai.domain.User
import zettai.domain.commands.AddToDoItem
import zettai.domain.commands.CreateToDoList
import zettai.domain.commands.RenameToDoList
import zettai.domain.errors.ConcurrentModification
import zettai.domain.errors.ListAlreadyExists
import zettai.domain.events.ListCreated

/**
 * 2 つの command が、どちらも保存の前に transaction の中で読み出す順序を、待ち合わせで作る。
 * 時間や実行の順序には頼らない。
 */
private fun <Tx> bothReadBeforeEitherWrites(store: EventStore<Tx>): EventStore<Tx> {
    val arrived = AtomicInteger(0)
    val gate = CompletableDeferred<Unit>()
    return store.copy(
        readStream = { tx, stream ->
            val events = store.readStream(tx, stream)
            if (arrived.incrementAndGet() >= 2) gate.complete(Unit)
            withTimeout(GATE_TIMEOUT_MS) { gate.await() }
            events
        },
    )
}

/**
 * 2 つの command が、どちらも読み終えてから、どちらも書き始める順序を作る。
 * command が触れる stream の数に依らない（名前変更は 2 つの stream を読む）。
 * 片方が書く前に拒否されるなど、もう片方が待ち続ける場合は、止まらずにタイムアウトで失敗する。
 */
private fun <Tx> bothReachAppendTogether(store: EventStore<Tx>): EventStore<Tx> {
    val arrived = AtomicInteger(0)
    val gate = CompletableDeferred<Unit>()
    return store.copy(
        append = { tx, versions, events ->
            if (arrived.incrementAndGet() >= 2) gate.complete(Unit)
            withTimeout(GATE_TIMEOUT_MS) { gate.await() }
            store.append(tx, versions, events)
        },
    )
}

private const val GATE_TIMEOUT_MS = 10_000L

/**
 * 並行する command の契約。
 * - PostgreSQL の transaction は並行して動く。読み出しを交差させると、後から書く側が版の競合で拒否される。
 * - in-memory の transaction は `Mutex` で直列になる。読み出しは交差せず、後の command は更新後の状態を見て判断する。
 * どちらの store でも、event は重複せず、更新は失われない。
 */
class ConcurrentCommandTest : StringSpec({
    val alice = User("alice")
    val book = ListName("book")
    val bookStream = StreamId(alice, book)

    "PostgreSQL: 読み出しが交差した並行する CreateToDoList は、後から書く側が ConcurrentModification で拒否される" {
        val store = freshStore("PostgreSQL")
        val hub = todoListHub(bothReadBeforeEitherWrites(store))

        val results = coroutineScope {
            listOf(
                async(Dispatchers.Default) { hub.execute(CreateToDoList(alice, book)) },
                async(Dispatchers.Default) { hub.execute(CreateToDoList(alice, book)) },
            ).awaitAll()
        }

        results.count { it.isRight() } shouldBe 1
        results.mapNotNull { it.leftOrNull() } shouldBe listOf(ConcurrentModification(listOf(bookStream)))
        store.eventsOf(bookStream).count { it is ListCreated } shouldBe 1
    }

    "in-memory: 並行する CreateToDoList は、後の側が更新後の状態を見て ListAlreadyExists で拒否される" {
        val store = freshStore("in-memory")
        val hub = todoListHub(store)

        val results = coroutineScope {
            listOf(
                async(Dispatchers.Default) { hub.execute(CreateToDoList(alice, book)) },
                async(Dispatchers.Default) { hub.execute(CreateToDoList(alice, book)) },
            ).awaitAll()
        }

        results.count { it.isRight() } shouldBe 1
        results.mapNotNull { it.leftOrNull() } shouldBe listOf(ListAlreadyExists(alice, book))
        store.eventsOf(bookStream).count { it is ListCreated } shouldBe 1
    }

    "PostgreSQL: 読み出しが交差した並行する AddToDoItem は、片方だけが成功し、更新は失われない" {
        val store = freshStore("PostgreSQL")
        val plain = todoListHub(store)
        plain.execute(CreateToDoList(alice, book)).getOrElse { error(it.message) }
        plain.execute(AddToDoItem(alice, book, ToDoItem("write chapter"))).getOrElse { error(it.message) }
        val hub = todoListHub(bothReadBeforeEitherWrites(store))

        val results = coroutineScope {
            listOf(
                async(Dispatchers.Default) { hub.execute(AddToDoItem(alice, book, ToDoItem("a"))) },
                async(Dispatchers.Default) { hub.execute(AddToDoItem(alice, book, ToDoItem("b"))) },
            ).awaitAll()
        }

        results.count { it.isRight() } shouldBe 1
        results.mapNotNull { it.leftOrNull() } shouldBe listOf(ConcurrentModification(listOf(bookStream)))
        plain.getList(alice, book).getOrElse { error(it.message) }.items.size shouldBe 2
    }

    "in-memory: 並行する AddToDoItem は、直列になり、どちらも失われない" {
        val store = freshStore("in-memory")
        val hub = todoListHub(store)
        hub.execute(CreateToDoList(alice, book)).getOrElse { error(it.message) }

        val results = coroutineScope {
            listOf(
                async(Dispatchers.Default) { hub.execute(AddToDoItem(alice, book, ToDoItem("a"))) },
                async(Dispatchers.Default) { hub.execute(AddToDoItem(alice, book, ToDoItem("b"))) },
            ).awaitAll()
        }

        results.count { it.isRight() } shouldBe 2
        hub.getList(alice, book).getOrElse { error(it.message) }.items.map { it.description }.toSet() shouldBe
            setOf("a", "b")
    }

    "PostgreSQL: 名前変更と、変名先と同じ名前の CreateToDoList が交差すると、片方だけが成功し、項目は 1 か所にしか残らない" {
        val store = freshStore("PostgreSQL")
        val plain = todoListHub(store)
        val novel = ListName("novel")
        plain.execute(CreateToDoList(alice, book)).getOrElse { error(it.message) }
        plain.execute(AddToDoItem(alice, book, ToDoItem("write chapter"))).getOrElse { error(it.message) }
        val hub = todoListHub(bothReachAppendTogether(store))

        val results = coroutineScope {
            listOf(
                async(Dispatchers.Default) { hub.execute(RenameToDoList(alice, book, novel)) },
                async(Dispatchers.Default) { hub.execute(CreateToDoList(alice, novel)) },
            ).awaitAll()
        }

        results.count { it.isRight() } shouldBe 1
        results.mapNotNull { it.leftOrNull() }.single().shouldBeInstanceOf<ConcurrentModification>()
        val itemsInBook = plain.getList(alice, book).fold({ 0 }, { it.items.size })
        val itemsInNovel = plain.getList(alice, novel).fold({ 0 }, { it.items.size })
        itemsInBook + itemsInNovel shouldBe 1
    }

    "PostgreSQL: 同じリストを別の名前へ同時に変えると、片方だけが成功し、項目は複製されない" {
        val store = freshStore("PostgreSQL")
        val plain = todoListHub(store)
        val archive = ListName("archive")
        val novel = ListName("novel")
        plain.execute(CreateToDoList(alice, book)).getOrElse { error(it.message) }
        plain.execute(AddToDoItem(alice, book, ToDoItem("write chapter"))).getOrElse { error(it.message) }
        val hub = todoListHub(bothReachAppendTogether(store))

        val results = coroutineScope {
            listOf(
                async(Dispatchers.Default) { hub.execute(RenameToDoList(alice, book, archive)) },
                async(Dispatchers.Default) { hub.execute(RenameToDoList(alice, book, novel)) },
            ).awaitAll()
        }

        results.count { it.isRight() } shouldBe 1
        results.mapNotNull { it.leftOrNull() } shouldBe listOf(ConcurrentModification(listOf(bookStream)))
        plain.getList(alice, book).isLeft() shouldBe true
        val remaining = listOf(archive, novel).mapNotNull { plain.getList(alice, it).getOrNull() }
        remaining.map { it.items.size } shouldBe listOf(1)
    }
})
