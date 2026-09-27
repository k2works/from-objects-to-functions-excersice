package zettai.persistence

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.User
import zettai.domain.events.ItemAdded
import zettai.domain.events.ListCreated
import zettai.domain.events.ToDoListEvent
import zettai.fp.Failure

/**
 * トランザクションの結合テスト。
 *
 * 「まとめて成功するか、何も残らないか」を確かめる。
 * 成功の確認より、失敗したときに何も残らないことの確認が漏れやすい。
 */
class TransactionTest {
    private val user = User("uberto")
    private val book = ListName("book")
    private val entityId = EntityId.of(user, book)

    @BeforeEach
    fun setUp() {
        TestDatabase.reset()
    }

    private fun storedCount(): Int =
        PostgresEventStore.countAll().runOn(TestDatabase::connect).fold({ error(it.message) }, { it })

    @Test
    fun `まとめて成功したら全件残る`() {
        val first: List<ToDoListEvent> = listOf(ListCreated(user, book))
        val second: List<ToDoListEvent> = listOf(ItemAdded(user, book, ToDoItem("write chapter")))

        val outcome = PostgresEventStore.append(entityId, first)
            .flatMap { PostgresEventStore.append(entityId, second) }
            .asHubAction()
            .runInTransaction(TestDatabase::connect)

        expectThat(outcome.fold({ null }, { "ok" })).isEqualTo("ok")
        expectThat(storedCount()).isEqualTo(2)
    }

    @Test
    fun `途中で失敗したら 1 件も残らない`() {
        val first: List<ToDoListEvent> = listOf(ListCreated(user, book))

        val outcome = PostgresEventStore.append(entityId, first)
            .flatMap<Unit> { error("2 つめの操作で失敗させる") }
            .asHubAction()
            .runInTransaction(TestDatabase::connect)

        expectThat(outcome).isA<Failure<*>>()
        expectThat(storedCount()).isEqualTo(0)
    }

    @Test
    fun `トランザクションを使わないと途中までの書き込みが残る`() {
        val first: List<ToDoListEvent> = listOf(ListCreated(user, book))

        // 同じ操作を runOn（autoCommit）で実行する。1 つめは commit されてしまう
        val outcome = PostgresEventStore.append(entityId, first)
            .flatMap<Unit> { error("2 つめの操作で失敗させる") }
            .runOn(TestDatabase::connect)

        expectThat(outcome).isA<Failure<*>>()

        // これがトランザクションを使う理由。失敗したのに 1 件残っている
        expectThat(storedCount()).isEqualTo(1)
    }
}
