package zettai.adapter.postgres

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.checkAll
import java.time.LocalDate
import zettai.arbEvents
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoStatus
import zettai.domain.User
import zettai.domain.events.ItemAdded
import zettai.domain.events.ItemStatusChanged
import zettai.domain.events.ListCreated
import zettai.genUsers

class PostgresEventStoreTest : StringSpec({
    val alice = User("alice")
    val bob = User("bob")
    val book = ListName("book")
    val shop = ListName("shop")

    beforeEach { SharedPostgres.reset() }

    "追記した event を同じ順序で読み戻す" {
        val store = postgresEventStore(SharedPostgres::connect)
        val events = listOf(
            ListCreated(alice, book),
            ItemAdded(alice, book, ToDoItem("write chapter")),
            ItemStatusChanged(alice, book, "write chapter", ToDoStatus.InProgress),
        )

        store.persist(events)

        store.readAggregate(alice, book) shouldBe events
    }

    "複数回に分けて追記しても追記順に読み戻す" {
        val store = postgresEventStore(SharedPostgres::connect)

        store.persist(listOf(ListCreated(alice, book)))
        store.persist(listOf(ItemAdded(alice, book, ToDoItem("a")), ItemAdded(alice, book, ToDoItem("b"))))

        store.readAggregate(alice, book) shouldBe listOf(
            ListCreated(alice, book),
            ItemAdded(alice, book, ToDoItem("a")),
            ItemAdded(alice, book, ToDoItem("b")),
        )
    }

    "別の集約の event は混ざらない" {
        val store = postgresEventStore(SharedPostgres::connect)
        store.persist(
            listOf(
                ListCreated(alice, book),
                ListCreated(alice, shop),
                ListCreated(bob, book),
            ),
        )

        store.readAggregate(alice, book) shouldBe listOf(ListCreated(alice, book))
        store.readAggregate(bob, shop) shouldBe emptyList()
    }

    "readUser は user の event だけを追記順に返す" {
        val store = postgresEventStore(SharedPostgres::connect)
        store.persist(
            listOf(
                ListCreated(alice, book),
                ListCreated(bob, book),
                ListCreated(alice, shop),
            ),
        )

        store.readUser(alice) shouldBe listOf(ListCreated(alice, book), ListCreated(alice, shop))
    }

    "区切り文字を含む名前でも別の集約が衝突しない" {
        val store = postgresEventStore(SharedPostgres::connect)
        val slashUser = User("a/b")
        val plainUser = User("a")
        store.persist(
            listOf(
                ListCreated(slashUser, ListName("c")),
                ListCreated(plainUser, ListName("b/c")),
                ListCreated(User("100%"), ListName("done")),
                ListCreated(User("100"), ListName("%25/done")),
            ),
        )

        store.readAggregate(slashUser, ListName("c")) shouldBe listOf(ListCreated(slashUser, ListName("c")))
        store.readAggregate(plainUser, ListName("b/c")) shouldBe listOf(ListCreated(plainUser, ListName("b/c")))
        store.readUser(plainUser) shouldBe listOf(ListCreated(plainUser, ListName("b/c")))
        store.readUser(User("100%")) shouldBe listOf(ListCreated(User("100%"), ListName("done")))
    }

    "引用符、バックスラッシュ、改行、絵文字、期日、null を含む event を往復できる" {
        val store = postgresEventStore(SharedPostgres::connect)
        val nasty = "say \"hi\" \\ back\nline 日本語 🚀 'single' <b>"
        val events = listOf(
            ListCreated(User(nasty), ListName(nasty)),
            ItemAdded(User(nasty), ListName(nasty), ToDoItem(nasty, LocalDate.of(2026, 11, 1), ToDoStatus.Blocked)),
            ItemAdded(User(nasty), ListName(nasty), ToDoItem("no due date", null, ToDoStatus.Done)),
            ItemStatusChanged(User(nasty), ListName(nasty), nasty, ToDoStatus.Done),
        )

        store.persist(events)

        store.readAggregate(User(nasty), ListName(nasty)) shouldBe events
    }

    "接続を作り直しても（再起動に相当）追記済みの event を読める" {
        val first = postgresEventStore(SharedPostgres::connect)
        first.persist(listOf(ListCreated(alice, book), ItemAdded(alice, book, ToDoItem("kept"))))

        createSchema(SharedPostgres::connect)
        val restarted = postgresEventStore(SharedPostgres::connect)

        restarted.readAggregate(alice, book) shouldBe listOf(
            ListCreated(alice, book),
            ItemAdded(alice, book, ToDoItem("kept")),
        )
    }

    "任意の event 列を往復でき、user ごとに追記順が保たれる" {
        checkAll(25, arbEvents) { events ->
            SharedPostgres.reset()
            val store = postgresEventStore(SharedPostgres::connect)

            store.persist(events)

            genUsers.forEach { user ->
                store.readUser(user) shouldBe events.filter { it.user == user }
            }
        }
    }
})
