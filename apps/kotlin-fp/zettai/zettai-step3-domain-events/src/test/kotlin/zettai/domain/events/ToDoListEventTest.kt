package zettai.domain.events

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoList
import zettai.domain.User

class ToDoListEventTest : StringSpec({
    val user = User("uberto")
    val listName = ListName("book")

    "イベント適用は元の状態を変えずに新しい状態を返す" {
        val empty = ToDoListState.empty
        val created = empty.apply(ListCreated(user, listName))
        val itemAdded = created.apply(ItemAdded(user, listName, ToDoItem("write chapter")))

        empty.listFor(user, listName) shouldBe null
        created.listFor(user, listName)?.items shouldBe emptyList()
        itemAdded.listFor(user, listName)?.items shouldBe listOf(ToDoItem("write chapter"))
    }

    "未作成のリストへの項目追加は状態を変えない" {
        val empty = ToDoListState.empty

        empty.apply(ItemAdded(user, listName, ToDoItem("write chapter"))) shouldBe empty
    }

    "factory は外部の可変 map と項目列から状態を切り離す" {
        val items = mutableListOf(ToDoItem("write chapter"))
        val lists = mutableMapOf((user to listName) to ToDoList(listName, items))
        val state = ToDoListState.from(lists)

        items += ToDoItem("publish book")
        lists.clear()

        state.listFor(user, listName)?.items shouldBe listOf(ToDoItem("write chapter"))
    }

    "同じ名前のリストをユーザーごとに分離する" {
        val alice = User("alice")
        val events = listOf(
            ListCreated(user, listName),
            ItemAdded(user, listName, ToDoItem("write chapter")),
            ListCreated(alice, listName),
            ItemAdded(alice, listName, ToDoItem("read chapter")),
        )
        val state = events.replayFrom(ToDoListState.empty)

        state.listFor(user, listName)?.items shouldBe listOf(ToDoItem("write chapter"))
        state.listFor(alice, listName)?.items shouldBe listOf(ToDoItem("read chapter"))
    }

    "重複する ListCreated はイベント順にリストを空へ戻す" {
        val events = listOf(
            ListCreated(user, listName),
            ItemAdded(user, listName, ToDoItem("write chapter")),
            ListCreated(user, listName),
        )

        events.replayFrom(ToDoListState.empty).listFor(user, listName)?.items shouldBe emptyList()
    }

    "再帰と fold は同じ状態を導出する" {
        val events = listOf(
            ListCreated(user, listName),
            ItemAdded(user, listName, ToDoItem("write chapter")),
            ItemAdded(user, listName, ToDoItem("review chapter")),
        )

        events.replayByRecursion(ToDoListState.empty) shouldBe
            events.replayFrom(ToDoListState.empty)
    }
})
