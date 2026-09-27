package zettai.domain.queries

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isNull
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoStatus
import zettai.domain.User
import zettai.domain.events.EventGenerator
import zettai.domain.events.ItemAdded
import zettai.domain.events.ItemStatusChanged
import zettai.domain.events.ListCreated
import zettai.domain.events.ToDoListEvent
import zettai.property.forAllRandom

class ToDoListProjectionTest {
    private val user = User("uberto")
    private val book = ListName("book")

    private fun projectionOf(vararg events: ToDoListEvent): ToDoListProjection =
        events.toList().projectFrom(ToDoListProjection.empty)

    @Test
    fun `イベントが無ければ空`() {
        expectThat(ToDoListProjection.empty.listsFor(user)).isEmpty()
    }

    @Test
    fun `リスト作成で行ができる`() {
        val projection = projectionOf(ListCreated(user, book))

        expectThat(projection.listsFor(user)).isEqualTo(listOf(ToDoListRow(book, itemCount = 0, doneCount = 0)))
    }

    @Test
    fun `項目数を数える`() {
        val projection = projectionOf(
            ListCreated(user, book),
            ItemAdded(user, book, ToDoItem("write chapter")),
            ItemAdded(user, book, ToDoItem("publish book"))
        )

        expectThat(projection.listsFor(user).single().itemCount).isEqualTo(2)
    }

    @Test
    fun `完了した項目数を数える`() {
        val projection = projectionOf(
            ListCreated(user, book),
            ItemAdded(user, book, ToDoItem("write chapter")),
            ItemAdded(user, book, ToDoItem("publish book")),
            ItemStatusChanged(user, book, "write chapter", ToDoStatus.Done)
        )

        expectThat(projection.listsFor(user).single().doneCount).isEqualTo(1)
    }

    @Test
    fun `作られていないリストへの項目追加は無視する`() {
        expectThat(projectionOf(ItemAdded(user, book, ToDoItem("write chapter"))).listsFor(user)).isEmpty()
    }

    @Test
    fun `他の利用者のリストは見えない`() {
        val projection = projectionOf(ListCreated(User("alice"), book))

        expectThat(projection.listsFor(user)).isEmpty()
        expectThat(projection.itemsFor(user, book)).isNull()
    }

    @Test
    fun `リスト名の順に並ぶ`() {
        val projection = projectionOf(
            ListCreated(user, ListName("work")),
            ListCreated(user, ListName("book")),
            ListCreated(user, ListName("shopping"))
        )

        expectThat(projection.listsFor(user).map { it.listName.name })
            .isEqualTo(listOf("book", "shopping", "work"))
    }
}

/**
 * 射影の map がファンクタであることを確かめる。
 *
 * 第 7 章の Outcome.map と同じ 2 つの法則。対象が変わっても形は同じ。
 */
class ToDoListProjectionFunctorTest {
    private val f: (ToDoListRow) -> Int = { it.itemCount }
    private val g: (Int) -> String = { "項目 $it 件" }

    @Test
    fun `恒等則が成り立つ`() {
        forAllRandom { random ->
            val projection = randomProjection(random)

            expectThat(projection.map { it }).isEqualTo(projection.map { it })
        }
    }

    @Test
    fun `合成則が成り立つ`() {
        forAllRandom { random ->
            val projection = randomProjection(random)

            expectThat(projection.map(f).map(g)).isEqualTo(projection.map { g(f(it)) })
        }
    }

    private fun randomProjection(random: kotlin.random.Random): ToDoListProjection =
        EventGenerator.events(random, random.nextInt(0, 8)).projectFrom(ToDoListProjection.empty)
}
