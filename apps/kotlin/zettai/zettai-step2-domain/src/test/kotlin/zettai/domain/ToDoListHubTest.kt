package zettai.domain

import java.time.LocalDate
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

/**
 * ハブのテスト。
 *
 * アダプタの実装クラスを 1 つも用意していない。必要な振る舞いはラムダで書く。
 */
class ToDoListHubTest {

    private val user = User("uberto")
    private val listName = ListName("book")
    private val items = listOf(ToDoItem("write chapter"))

    @Test
    fun `リストを取り出す`() {
        val hub = ToDoListHub { u, l -> if (u == user && l == listName) ToDoList(listName, items) else null }

        expectThat(hub.getList(user, listName)?.items).isEqualTo(items)
    }

    @Test
    fun `見つからなければ null`() {
        val hub = ToDoListHub { _, _ -> null }

        expectThat(hub.getList(user, ListName("missing"))).isNull()
    }

    @Test
    fun `アダプタが呼ばれた回数を数えられる`() {
        var calls = 0
        val hub = ToDoListHub { _, _ ->
            calls++
            null
        }

        hub.getList(user, listName)
        hub.getList(user, listName)

        expectThat(calls).isEqualTo(2)
    }
}

class ToDoItemTest {

    @Test
    fun `項目は既定で Todo 状態`() {
        expectThat(ToDoItem("write chapter").status).isEqualTo(ToDoStatus.Todo)
    }

    @Test
    fun `期限は任意`() {
        expectThat(ToDoItem("write chapter").dueDate).isNull()
        expectThat(ToDoItem("write chapter", LocalDate.of(2026, 12, 31)).dueDate)
            .isEqualTo(LocalDate.of(2026, 12, 31))
    }
}
