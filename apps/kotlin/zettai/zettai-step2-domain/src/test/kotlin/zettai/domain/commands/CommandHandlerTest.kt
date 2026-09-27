package zettai.domain.commands

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEmpty
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

class CommandHandlerTest {

    private val user = User("uberto")
    private val listName = ListName("book")

    private fun stateOf(vararg events: ToDoListEvent): ToDoListState =
        events.toList().replayFrom(ToDoListState.empty)

    @Test
    fun `リスト作成でリスト作成イベントが起きる`() {
        val events = handle(CreateToDoList(user, listName), ToDoListState.empty)

        expectThat(events).isEqualTo(listOf(ListCreated(user, listName)))
    }

    @Test
    fun `既にあるリストは作れない`() {
        val events = handle(CreateToDoList(user, listName), stateOf(ListCreated(user, listName)))

        expectThat(events).isEmpty()
    }

    @Test
    fun `項目追加で項目追加イベントが起きる`() {
        val item = ToDoItem("write chapter")
        val events = handle(AddToDoItem(user, listName, item), stateOf(ListCreated(user, listName)))

        expectThat(events).isEqualTo(listOf(ItemAdded(user, listName, item)))
    }

    @Test
    fun `無いリストには項目を追加できない`() {
        val events = handle(AddToDoItem(user, listName, ToDoItem("write chapter")), ToDoListState.empty)

        expectThat(events).isEmpty()
    }

    @Test
    fun `許される状態変更はイベントになる`() {
        val state = stateOf(
            ListCreated(user, listName),
            ItemAdded(user, listName, ToDoItem("write chapter"))
        )

        val events = handle(ChangeItemStatus(user, listName, "write chapter", ToDoStatus.InProgress), state)

        expectThat(events)
            .isEqualTo(listOf(ItemStatusChanged(user, listName, "write chapter", ToDoStatus.InProgress)))
    }

    @Test
    fun `許されない状態変更はイベントにならない`() {
        val state = stateOf(
            ListCreated(user, listName),
            ItemAdded(user, listName, ToDoItem("write chapter")),
            ItemStatusChanged(user, listName, "write chapter", ToDoStatus.Done)
        )

        val events = handle(ChangeItemStatus(user, listName, "write chapter", ToDoStatus.InProgress), state)

        expectThat(events).isEmpty()
    }

    @Test
    fun `無い項目の状態は変えられない`() {
        val events = handle(
            ChangeItemStatus(user, listName, "missing", ToDoStatus.InProgress),
            stateOf(ListCreated(user, listName))
        )

        expectThat(events).isEmpty()
    }
}
