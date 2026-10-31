package zettai.application

import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoStatus
import zettai.domain.User
import zettai.domain.commands.AddToDoItem
import zettai.domain.commands.ChangeItemStatus
import zettai.domain.commands.CreateToDoList
import zettai.domain.commands.ToDoListCommand
import zettai.domain.errors.InvalidTransition
import zettai.domain.errors.ItemNotFound
import zettai.domain.errors.ListAlreadyExists
import zettai.domain.errors.ListNotFound
import zettai.domain.errors.ZettaiError
import zettai.domain.events.ItemAdded
import zettai.domain.events.ItemStatusChanged
import zettai.domain.events.ListCreated
import zettai.domain.events.ToDoListEvent
import zettai.domain.events.ToDoListState
import zettai.domain.events.replayFrom

class ToDoListHubTest : StringSpec({
    "constructor function は検証後のイベントだけを一度永続化する" {
        runTest {
            val user = User("uberto")
            val listName = ListName("book")
            val item = ToDoItem("write chapter")
            val state = listOf(ListCreated(user, listName)).replayFrom(ToDoListState.empty)
            val persisted = mutableListOf<List<ToDoListEvent>>()
            val hub = todoListHub(
                fetchList = { _, _ -> ListNotFound(user, listName).left() },
                fetchState = { state },
                persist = { persisted += it },
            )

            hub.execute(AddToDoItem(user, listName, item)) shouldBe
                listOf(ItemAdded(user, listName, item)).right()
            persisted shouldBe listOf(listOf(ItemAdded(user, listName, item)))
        }
    }

    val user = User("uberto")
    val listName = ListName("book")
    val item = ToDoItem("write chapter")
    val created = ListCreated(user, listName)
    val added = ItemAdded(user, listName, item)
    val done = ItemStatusChanged(user, listName, item.description, ToDoStatus.Done)

    data class Rejection(
        val events: List<ToDoListEvent>,
        val command: ToDoListCommand,
        val error: ZettaiError,
    )

    listOf(
        Rejection(emptyList(), AddToDoItem(user, listName, item), ListNotFound(user, listName)),
        Rejection(listOf(created), CreateToDoList(user, listName), ListAlreadyExists(user, listName)),
        Rejection(
            listOf(created),
            ChangeItemStatus(user, listName, item.description, ToDoStatus.Done),
            ItemNotFound(user, listName, item.description),
        ),
        Rejection(
            listOf(created, added, done),
            ChangeItemStatus(user, listName, item.description, ToDoStatus.InProgress),
            InvalidTransition(item.description, ToDoStatus.Done, ToDoStatus.InProgress),
        ),
    ).forEach { rejection ->
        "${rejection.error::class.simpleName} で拒否したときはイベントを永続化しない" {
            runTest {
                var persistCalls = 0
                val hub = todoListHub(
                    fetchList = { _, _ -> ListNotFound(user, listName).left() },
                    fetchState = { rejection.events.replayFrom(ToDoListState.empty) },
                    persist = { persistCalls += 1 },
                )

                hub.execute(rejection.command) shouldBe rejection.error.left()
                persistCalls shouldBe 0
            }
        }
    }
})
