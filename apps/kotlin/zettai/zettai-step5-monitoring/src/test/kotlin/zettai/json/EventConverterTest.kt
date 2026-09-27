package zettai.json

import java.time.LocalDate
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoStatus
import zettai.domain.User
import zettai.domain.events.EventGenerator
import zettai.domain.events.ItemAdded
import zettai.domain.events.ItemStatusChanged
import zettai.domain.events.ListCreated
import zettai.domain.events.ListRenamed
import zettai.fp.Failure
import zettai.fp.Success
import zettai.property.forAllRandom

class EventConverterTest {
    private val user = User("uberto")
    private val book = ListName("book")

    @Test
    fun `リスト作成を往復できる`() {
        val event = ListCreated(user, book)

        expectThat(eventConverter.roundTrip(event)).isEqualTo(Success(event))
    }

    @Test
    fun `項目追加を往復できる`() {
        val event = ItemAdded(user, book, ToDoItem("write chapter", LocalDate.of(2026, 12, 31), ToDoStatus.InProgress))

        expectThat(eventConverter.roundTrip(event)).isEqualTo(Success(event))
    }

    @Test
    fun `期限が無い項目も往復できる`() {
        val event = ItemAdded(user, book, ToDoItem("write chapter"))

        expectThat(eventConverter.roundTrip(event)).isEqualTo(Success(event))
    }

    @Test
    fun `リスト名変更を往復できる`() {
        val event = ListRenamed(user, book, ListName("reading"))

        expectThat(eventConverter.roundTrip(event)).isEqualTo(Success(event))
    }

    @Test
    fun `状態変更を往復できる`() {
        val event = ItemStatusChanged(user, book, "write chapter", ToDoStatus.Done)

        expectThat(eventConverter.roundTrip(event)).isEqualTo(Success(event))
    }

    @Test
    fun `どんな出来事でも往復できる`() {
        forAllRandom { random ->
            EventGenerator.events(random, random.nextInt(1, 6)).forEach { event ->
                expectThat(eventConverter.roundTrip(event)).isEqualTo(Success(event))
            }
        }
    }

    @Test
    fun `知らない種類は失敗を返す`() {
        val result = eventConverter.parse("""{"eventType":"Unknown","user":"uberto","listName":"book"}""")

        expectThat(result).isA<Failure<*>>()
    }

    @Test
    fun `JSONB が正規化した形でも読める`() {
        // PostgreSQL は保存時に JSON を正規化し、コロンの後に空白を入れる
        val normalized = """{"eventType": "ListCreated", "user": "uberto", "listName": "book"}"""

        expectThat(eventConverter.parse(normalized)).isEqualTo(Success(ListCreated(user, book)))
    }
}
