package zettai.application

import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoList
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
import zettai.domain.queries.ToDoListRow

class ToDoListHubTest : StringSpec({
    val user = User("uberto")
    val listName = ListName("book")
    val item = ToDoItem("write chapter")
    val created = ListCreated(user, listName)
    val added = ItemAdded(user, listName, item)
    val done = ItemStatusChanged(user, listName, item.description, ToDoStatus.Done)

    "command 側は検証後のイベントだけを一度永続化する" {
        runTest {
            val persisted = mutableListOf<List<ToDoListEvent>>()
            val hub = todoListHub(
                readAggregate = { _, _ -> listOf(created) },
                readUser = { error("command は user 単位で読まない") },
                persist = { persisted += it },
            )

            hub.execute(AddToDoItem(user, listName, item)) shouldBe listOf(added).right()
            persisted shouldBe listOf(listOf(added))
        }
    }

    "command 側は指定した集約のイベントだけを読む" {
        runTest {
            val requested = mutableListOf<Pair<User, ListName>>()
            val hub = todoListHub(
                readAggregate = { u, l ->
                    requested += u to l
                    listOf(created)
                },
                readUser = { error("command は user 単位で読まない") },
                persist = {},
            )

            hub.execute(AddToDoItem(user, listName, item))

            requested shouldBe listOf(user to listName)
        }
    }

    "他の集約のイベントが混ざっても判断は変わらない" {
        runTest {
            val other = ListName("shop")
            val hub = todoListHub(
                readAggregate = { _, _ -> listOf(ListCreated(user, other)) },
                readUser = { error("command は user 単位で読まない") },
                persist = {},
            )

            hub.execute(AddToDoItem(user, listName, item)) shouldBe
                ListNotFound(user, listName).left()
        }
    }

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
                    readAggregate = { _, _ -> rejection.events },
                    readUser = { error("command は user 単位で読まない") },
                    persist = { persistCalls += 1 },
                )

                hub.execute(rejection.command) shouldBe rejection.error.left()
                persistCalls shouldBe 0
            }
        }
    }

    "query 側の getList は集約のイベントから ToDoList を導く" {
        runTest {
            val hub = todoListHub(
                readAggregate = { _, _ -> listOf(created, added) },
                readUser = { error("getList は user 単位で読まない") },
                persist = { error("query は永続化しない") },
            )

            hub.getList(user, listName) shouldBe ToDoList(listName, listOf(item)).right()
        }
    }

    "イベントのないリストは ListNotFound になる" {
        runTest {
            val hub = todoListHub(
                readAggregate = { _, _ -> emptyList() },
                readUser = { error("getList は user 単位で読まない") },
                persist = { error("query は永続化しない") },
            )

            hub.getList(user, listName) shouldBe ListNotFound(user, listName).left()
        }
    }

    "query 側の listRows は user のイベントを射影する" {
        runTest {
            val hub = todoListHub(
                readAggregate = { _, _ -> error("listRows は集約単位で読まない") },
                readUser = { listOf(created, added, done) },
                persist = { error("query は永続化しない") },
            )

            hub.listRows(user) shouldBe listOf(ToDoListRow(listName, itemCount = 1, doneCount = 1))
        }
    }
})
