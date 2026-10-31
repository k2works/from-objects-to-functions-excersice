package zettai.adapter.postgres

import arrow.core.getOrElse
import arrow.core.right
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import java.sql.SQLException
import java.time.LocalDate
import zettai.application.EventStore
import zettai.application.appendAuto
import zettai.arbEvents
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoStatus
import zettai.domain.User
import zettai.domain.errors.PersistenceFailure
import zettai.domain.events.ItemAdded
import zettai.domain.events.ItemStatusChanged
import zettai.domain.events.ListCreated
import zettai.domain.events.ToDoListEvent
import zettai.genUsers

private suspend fun EventStore<*>.events(user: User, listName: ListName): List<ToDoListEvent> =
    readAggregate(user, listName).getOrElse { error(it.message) }

private suspend fun <Tx> EventStore<Tx>.append(events: List<ToDoListEvent>) {
    appendAuto(events).getOrElse { error(it.message) }
}

private suspend fun EventStore<*>.eventsOf(user: User): List<ToDoListEvent> =
    readUser(user).getOrElse { error(it.message) }

private fun insertRaw(eventType: String, payload: String) {
    SharedPostgres.connect().use { connection ->
        connection.prepareStatement(
            "INSERT INTO todo_list_event (entity_id, version, event_type, payload) " +
                "VALUES ('alice/book', 2, ?, ?::jsonb)",
        ).use { statement ->
            statement.setString(1, eventType)
            statement.setString(2, payload)
            statement.executeUpdate()
        }
    }
}

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

        store.append(events)

        store.events(alice, book) shouldBe events
    }

    "追記は成功を返す" {
        val store = postgresEventStore(SharedPostgres::connect)

        store.appendAuto(listOf(ListCreated(alice, book))) shouldBe Unit.right()
        createSchema(SharedPostgres::connect) shouldBe Unit.right()
    }

    "途中で失敗した batch は 1 件も残らない" {
        val store = postgresEventStore(SharedPostgres::connect)
        // NUL 文字は PostgreSQL の text に入らないので、2 件目の INSERT が失敗する
        val result = store.appendAuto(
            listOf(ListCreated(alice, book), ItemAdded(alice, book, ToDoItem("a\u0000b"))),
        )

        result.leftOrNull().shouldBeInstanceOf<PersistenceFailure>()
        store.events(alice, book) shouldBe emptyList()
    }

    "複数回に分けて追記しても追記順に読み戻す" {
        val store = postgresEventStore(SharedPostgres::connect)

        store.append(listOf(ListCreated(alice, book)))
        store.append(listOf(ItemAdded(alice, book, ToDoItem("a")), ItemAdded(alice, book, ToDoItem("b"))))

        store.events(alice, book) shouldBe listOf(
            ListCreated(alice, book),
            ItemAdded(alice, book, ToDoItem("a")),
            ItemAdded(alice, book, ToDoItem("b")),
        )
    }

    "別の集約の event は混ざらない" {
        val store = postgresEventStore(SharedPostgres::connect)
        store.append(
            listOf(
                ListCreated(alice, book),
                ListCreated(alice, shop),
                ListCreated(bob, book),
            ),
        )

        store.events(alice, book) shouldBe listOf(ListCreated(alice, book))
        store.events(bob, shop) shouldBe emptyList()
    }

    "readUser は user の event だけを追記順に返す" {
        val store = postgresEventStore(SharedPostgres::connect)
        store.append(
            listOf(
                ListCreated(alice, book),
                ListCreated(bob, book),
                ListCreated(alice, shop),
            ),
        )

        store.eventsOf(alice) shouldBe listOf(ListCreated(alice, book), ListCreated(alice, shop))
    }

    "区切り文字を含む名前でも別の集約が衝突しない" {
        val store = postgresEventStore(SharedPostgres::connect)
        val slashUser = User("a/b")
        val plainUser = User("a")
        store.append(
            listOf(
                ListCreated(slashUser, ListName("c")),
                ListCreated(plainUser, ListName("b/c")),
                ListCreated(User("100%"), ListName("done")),
                ListCreated(User("100"), ListName("%25/done")),
            ),
        )

        store.events(slashUser, ListName("c")) shouldBe listOf(ListCreated(slashUser, ListName("c")))
        store.events(plainUser, ListName("b/c")) shouldBe listOf(ListCreated(plainUser, ListName("b/c")))
        store.eventsOf(plainUser) shouldBe listOf(ListCreated(plainUser, ListName("b/c")))
        store.eventsOf(User("100%")) shouldBe listOf(ListCreated(User("100%"), ListName("done")))
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

        store.append(events)

        store.events(User(nasty), ListName(nasty)) shouldBe events
    }

    "スキーマを作り直す処理を再実行しても、追記済みの event は消えない" {
        val first = postgresEventStore(SharedPostgres::connect)
        first.append(listOf(ListCreated(alice, book), ItemAdded(alice, book, ToDoItem("kept"))))

        createSchema(SharedPostgres::connect)
        val restarted = postgresEventStore(SharedPostgres::connect)

        restarted.events(alice, book) shouldBe listOf(
            ListCreated(alice, book),
            ItemAdded(alice, book, ToDoItem("kept")),
        )
    }

    "区切り文字や空文字を含む名前の任意の event 列を往復でき、集約ごとに追記順が保たれる" {
        val names = Arb.element("a", "a/b", "b/c", "100%", "%25", "", "日本語")
        val nastyEvents: Arb<List<ToDoListEvent>> = Arb.list(
            Arb.choice(
                Arb.bind(names, names) { u, l -> ListCreated(User(u), ListName(l)) },
                Arb.bind(names, names, names) { u, l, d -> ItemAdded(User(u), ListName(l), ToDoItem(d)) },
            ),
            0..20,
        )

        checkAll(25, nastyEvents) { events ->
            SharedPostgres.reset()
            val store = postgresEventStore(SharedPostgres::connect)
            store.append(events)

            events.map { it.user to it.listName }.distinct().forEach { (user, listName) ->
                store.events(user, listName) shouldBe events.filter { it.user == user && it.listName == listName }
            }
            events.map { it.user }.distinct().forEach { user ->
                store.eventsOf(user) shouldBe events.filter { it.user == user }
            }
        }
    }

    "任意の event 列を往復でき、user ごとに追記順が保たれる" {
        checkAll(25, arbEvents) { events ->
            SharedPostgres.reset()
            val store = postgresEventStore(SharedPostgres::connect)

            store.append(events)

            genUsers.forEach { user ->
                store.eventsOf(user) shouldBe events.filter { it.user == user }
            }
        }
    }

    "接続できないときは例外ではなく PersistenceFailure を返す" {
        val store = postgresEventStore { throw SQLException("connection refused: jdbc:postgresql://db.internal") }

        store.readAggregate(alice, book).leftOrNull().shouldBeInstanceOf<PersistenceFailure>()
        store.readUser(alice).leftOrNull().shouldBeInstanceOf<PersistenceFailure>()
        store.appendAuto(listOf(ListCreated(alice, book))).leftOrNull().shouldBeInstanceOf<PersistenceFailure>()
    }

    "テーブルがないときは追記も読み出しも PersistenceFailure を返す" {
        SharedPostgres.connect().use { it.createStatement().use { s -> s.execute("DROP TABLE todo_list_event") } }
        val store = postgresEventStore(SharedPostgres::connect)

        store.appendAuto(listOf(ListCreated(alice, book))).leftOrNull().shouldBeInstanceOf<PersistenceFailure>()
        store.readAggregate(alice, book).leftOrNull().shouldBeInstanceOf<PersistenceFailure>()
    }

    listOf(
        "未知の event_type" to ("Unknown" to """{"user":"alice","listName":"book"}"""),
        "user が無い payload" to ("ListCreated" to """{"listName":"book"}"""),
        "不正な status" to (
            "ItemAdded" to
                """{"user":"alice","listName":"book","item":{"description":"x","dueDate":null,"status":"Bogus"}}"""
            ),
        "不正な期日" to (
            "ItemAdded" to
                """{"user":"alice","listName":"book","item":{"description":"x","dueDate":"bad","status":"Todo"}}"""
            ),
        "item が無い ItemAdded" to ("ItemAdded" to """{"user":"alice","listName":"book"}"""),
    ).forEach { (name, row) ->
        "壊れた行（$name）は読み出し時に PersistenceFailure を返し、正しい行も黙って捨てない" {
            val store = postgresEventStore(SharedPostgres::connect)
            store.append(listOf(ListCreated(alice, book)))
            insertRaw(row.first, row.second)

            store.readAggregate(alice, book).leftOrNull().shouldBeInstanceOf<PersistenceFailure>()
        }
    }

    "PersistenceFailure は原因を reason に持つ" {
        val store = postgresEventStore { throw SQLException("connection refused") }

        val failure = store.readUser(alice).leftOrNull()

        failure shouldBe PersistenceFailure("SQLException: connection refused")
    }
})
