package zettai.json

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEqualTo
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.User
import zettai.domain.events.ItemAdded
import zettai.fp.Failure
import zettai.fp.Success

/**
 * parse が Outcome を返すという約束を、壊れた入力で確かめる。
 *
 * 型に「失敗しうる」と書いておきながら例外で落ちると、
 * 呼び出し側は fold を書いても守られない（第 7 章の主張が崩れる）。
 */
class EventConverterFailureTest {

    @Test
    fun `空の JSON は Failure になる`() {
        expectThat(eventConverter.parse("{}")).isA<Failure<*>>()
    }

    @Test
    fun `必須の項目が欠けていると Failure になる`() {
        val json = """{"eventType":"ItemAdded","user":"uberto","listName":"book"}"""

        expectThat(eventConverter.parse(json)).isA<Failure<*>>()
    }

    @Test
    fun `知らない状態名は Failure になる`() {
        val json = """{"eventType":"ItemAdded","user":"uberto","listName":"book",""" +
            """"description":"write","dueDate":null,"status":"UNKNOWN"}"""

        expectThat(eventConverter.parse(json)).isA<Failure<*>>()
    }

    @Test
    fun `読めない日付は Failure になる`() {
        val json = """{"eventType":"ItemAdded","user":"uberto","listName":"book",""" +
            """"description":"write","dueDate":"きのう","status":"Todo"}"""

        expectThat(eventConverter.parse(json)).isA<Failure<*>>()
    }

    @Test
    fun `知らない出来事の種類は Failure になる`() {
        val json = """{"eventType":"ListVanished","user":"uberto","listName":"book"}"""

        expectThat(eventConverter.parse(json)).isA<Failure<*>>()
    }

    /**
     * 扱わなかったことを、テストで見えるようにする。
     *
     * この Converter は値をエスケープしない（[ADR-012] の「失うもの」）。
     * 黙って壊れるのではなく「壊れること」をテストに書いておけば、
     * 実務で使う人がここを踏む前に気づける。
     */
    @Test
    fun `引用符を含む説明は往復できない（エスケープを扱っていない）`() {
        val event = ItemAdded(User("uberto"), ListName("book"), ToDoItem("""say "hi""""))

        expectThat(eventConverter.roundTrip(event)).isNotEqualTo(Success(event))
    }

    /**
     * 保存する eventType の文字列を固定する。
     *
     * render はクラス名（`event::class.simpleName`）を書いている。
     * 往復のテストだけでは、クラスを改名しても書く側と読む側が同時に変わるので
     * green のままになる。**保存済みのデータが読めなくなるのはそのときだ。**
     */
    @Test
    fun `保存する出来事の種類の名前は変えられない`() {
        val rendered = eventConverter.render(ItemAdded(User("uberto"), ListName("book"), ToDoItem("write")))

        expectThat(rendered.toJsonObject().text("eventType")).isEqualTo("ItemAdded")
    }

    @Test
    fun `保存済みの種類の名前を読み戻せる`() {
        listOf("ListCreated", "ItemAdded", "ListRenamed", "ItemStatusChanged").forEach { type ->
            val json = """{"eventType":"$type","user":"uberto","listName":"book",""" +
                """"newName":"reading","description":"write","dueDate":null,"status":"Todo","newStatus":"Done"}"""

            expectThat(eventConverter.parse(json)).isA<Success<*>>()
        }
    }
}
