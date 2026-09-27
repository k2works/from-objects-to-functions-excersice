package zettai.persistence

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoStatus
import zettai.domain.User
import zettai.domain.events.ItemAdded
import zettai.domain.events.ItemStatusChanged
import zettai.domain.events.ListCreated
import zettai.domain.events.ToDoListEvent
import zettai.domain.events.ToDoListState
import zettai.domain.events.replayFrom
import zettai.fp.Failure
import zettai.fp.Success

/**
 * PostgreSQL への永続化の結合テスト。
 *
 * docker-compose.yml の zettai-db サービスが起動している必要がある。
 */
class PostgresEventStoreTest {
    private val user = User("uberto")
    private val book = ListName("book")
    private val entityId = EntityId.of(user, book)

    @BeforeEach
    fun setUp() {
        TestDatabase.reset()
    }

    private fun readAll() = PostgresEventStore.readAll().runOn(TestDatabase::connect)

    @Test
    fun `出来事が無ければ空`() {
        expectThat(readAll()).isEqualTo(Success(emptyList<ToDoListEvent>()))
    }

    @Test
    fun `追記した出来事を読み戻せる`() {
        val events: List<ToDoListEvent> = listOf(
            ListCreated(user, book),
            ItemAdded(user, book, ToDoItem("write chapter"))
        )

        PostgresEventStore.append(entityId, events).runOn(TestDatabase::connect)

        expectThat(readAll()).isEqualTo(Success(events))
    }

    @Test
    fun `追記順に並んで返る`() {
        val events: List<ToDoListEvent> = listOf(
            ListCreated(user, book),
            ItemAdded(user, book, ToDoItem("write chapter")),
            ItemAdded(user, book, ToDoItem("publish book")),
            ItemStatusChanged(user, book, "write chapter", ToDoStatus.Done)
        )

        events.forEach { PostgresEventStore.append(entityId, listOf(it)).runOn(TestDatabase::connect) }

        expectThat(readAll()).isEqualTo(Success(events))
    }

    @Test
    fun `読み戻した出来事から状態を復元できる`() {
        val events: List<ToDoListEvent> = listOf(
            ListCreated(user, book),
            ItemAdded(user, book, ToDoItem("write chapter")),
            ItemStatusChanged(user, book, "write chapter", ToDoStatus.InProgress)
        )
        PostgresEventStore.append(entityId, events).runOn(TestDatabase::connect)

        val restored = readAll().fold({ error(it.message) }, { it.replayFrom(ToDoListState.empty) })

        expectThat(restored.listFor(user, book)?.items?.single()?.status).isEqualTo(ToDoStatus.InProgress)
    }

    @Test
    fun `期限つきの項目を往復できる`() {
        val item = ToDoItem("write chapter", java.time.LocalDate.of(2026, 12, 31))
        PostgresEventStore.append(entityId, listOf(ItemAdded(user, book, item))).runOn(TestDatabase::connect)

        expectThat(readAll()).isEqualTo(Success(listOf(ItemAdded(user, book, item))))
    }

    @Test
    fun `接続できなければ失敗を返す`() {
        val action = PostgresEventStore.readAll()

        val outcome = action.runOn { error("接続できません") }

        expectThat(outcome).isA<Failure<*>>()
    }
}
