package zettai.domain.commands

import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoList
import zettai.domain.ToDoStatus
import zettai.domain.User
import zettai.domain.errors.InvalidTransition
import zettai.domain.errors.ItemNotFound
import zettai.domain.errors.ListAlreadyExists
import zettai.domain.errors.ListNotFound
import zettai.domain.events.ItemAdded
import zettai.domain.events.ItemStatusChanged
import zettai.domain.events.ListCreated
import zettai.domain.events.ToDoListEvent
import zettai.domain.events.ToDoListState
import zettai.domain.events.replayFrom

class CommandHandlerTest : StringSpec({
    val user = User("uberto")
    val listName = ListName("book")
    val item = ToDoItem("write chapter")

    fun stateOf(vararg events: ToDoListEvent): ToDoListState =
        events.toList().replayFrom(ToDoListState.empty)

    "新しいリストの作成を ListCreated に決定する" {
        handle(CreateToDoList(user, listName), ToDoListState.empty) shouldBe
            listOf(ListCreated(user, listName)).right()
    }

    "既存リストの再作成を具体的な理由で拒否する" {
        val state = stateOf(ListCreated(user, listName))

        handle(CreateToDoList(user, listName), state) shouldBe
            ListAlreadyExists(user, listName).left()
    }

    "存在するリストへの項目追加を ItemAdded に決定する" {
        val state = stateOf(ListCreated(user, listName))

        handle(AddToDoItem(user, listName, item), state) shouldBe
            listOf(ItemAdded(user, listName, item)).right()
    }

    "存在しないリストへの項目追加を拒否する" {
        handle(AddToDoItem(user, listName, item), ToDoListState.empty) shouldBe
            ListNotFound(user, listName).left()
    }

    "存在しないリストの項目の状態変更を拒否する" {
        handle(ChangeItemStatus(user, listName, item.description, ToDoStatus.Done), ToDoListState.empty) shouldBe
            ListNotFound(user, listName).left()
    }

    "state を作った後に元の可変リストを変えても判断は変わらない" {
        val items = mutableListOf(item)
        val lists = mutableMapOf((user to listName) to ToDoList(listName, items))
        val state = ToDoListState.from(lists)

        items.clear()
        lists.clear()

        handle(ChangeItemStatus(user, listName, item.description, ToDoStatus.InProgress), state) shouldBe
            listOf(
                ItemStatusChanged(user, listName, item.description, ToDoStatus.InProgress),
            ).right()
    }

    "存在しない項目の状態変更を拒否する" {
        val state = stateOf(ListCreated(user, listName))

        handle(ChangeItemStatus(user, listName, item.description, ToDoStatus.Done), state) shouldBe
            ItemNotFound(user, listName, item.description).left()
    }

    "許される状態変更を ItemStatusChanged に決定する" {
        val state = stateOf(ListCreated(user, listName), ItemAdded(user, listName, item))

        handle(ChangeItemStatus(user, listName, item.description, ToDoStatus.InProgress), state) shouldBe
            listOf(
                ItemStatusChanged(user, listName, item.description, ToDoStatus.InProgress),
            ).right()
    }

    "完了した項目の再開を拒否する" {
        val state = stateOf(
            ListCreated(user, listName),
            ItemAdded(user, listName, item),
            ItemStatusChanged(user, listName, item.description, ToDoStatus.Done),
        )

        handle(ChangeItemStatus(user, listName, item.description, ToDoStatus.InProgress), state) shouldBe
            InvalidTransition(item.description, ToDoStatus.Done, ToDoStatus.InProgress).left()
    }
})
