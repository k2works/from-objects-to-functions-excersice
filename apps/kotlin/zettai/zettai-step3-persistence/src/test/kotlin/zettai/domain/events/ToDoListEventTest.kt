package zettai.domain.events

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isNull
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.User

class ToDoListEventTest {

    private val user = User("uberto")
    private val listName = ListName("book")

    @Test
    fun `イベントが無ければ状態は空`() {
        expectThat(emptyList<ToDoListEvent>().replayFrom(ToDoListState.empty).lists).isEmpty()
    }

    @Test
    fun `リスト作成でリストができる`() {
        val state = listOf(ListCreated(user, listName)).replayFrom(ToDoListState.empty)

        expectThat(state.listFor(user, listName)?.items).isEqualTo(emptyList())
    }

    @Test
    fun `項目追加で項目が増える`() {
        val events = listOf(
            ListCreated(user, listName),
            ItemAdded(user, listName, ToDoItem("write chapter")),
            ItemAdded(user, listName, ToDoItem("publish book"))
        )

        val state = events.replayFrom(ToDoListState.empty)

        expectThat(state.listFor(user, listName)?.items)
            .isEqualTo(listOf(ToDoItem("write chapter"), ToDoItem("publish book")))
    }

    @Test
    fun `作られていないリストへの項目追加は無視する`() {
        val state = listOf(ItemAdded(user, listName, ToDoItem("write chapter")))
            .replayFrom(ToDoListState.empty)

        expectThat(state.listFor(user, listName)).isNull()
    }

    @Test
    fun `再帰と fold は同じ結果になる`() {
        val events = listOf(
            ListCreated(user, listName),
            ItemAdded(user, listName, ToDoItem("write chapter"))
        )

        expectThat(events.replayByRecursion(ToDoListState.empty))
            .isEqualTo(events.replayFrom(ToDoListState.empty))
    }
}
