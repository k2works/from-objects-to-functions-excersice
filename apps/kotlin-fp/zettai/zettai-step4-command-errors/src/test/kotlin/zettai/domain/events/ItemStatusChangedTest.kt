package zettai.domain.events

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoStatus
import zettai.domain.User

class ItemStatusChangedTest : StringSpec({
    "状態変更イベントは元の状態を変えず新しい状態を返す" {
        val user = User("uberto")
        val listName = ListName("book")
        val original = listOf(
            ListCreated(user, listName),
            ItemAdded(user, listName, ToDoItem("write chapter")),
        ).replayFrom(ToDoListState.empty)

        val changed = original.apply(
            ItemStatusChanged(user, listName, "write chapter", ToDoStatus.InProgress),
        )

        original.listFor(user, listName)?.items?.single()?.status shouldBe ToDoStatus.Todo
        changed.listFor(user, listName)?.items?.single()?.status shouldBe ToDoStatus.InProgress
    }

    "同じ description の項目が複数あっても最初の項目だけを変更する" {
        val user = User("uberto")
        val listName = ListName("book")
        val original = listOf(
            ListCreated(user, listName),
            ItemAdded(user, listName, ToDoItem("review", status = ToDoStatus.Todo)),
            ItemAdded(user, listName, ToDoItem("review", status = ToDoStatus.Done)),
        ).replayFrom(ToDoListState.empty)

        val changed = original.apply(
            ItemStatusChanged(user, listName, "review", ToDoStatus.InProgress),
        )

        changed.listFor(user, listName)?.items?.map { it.status } shouldBe
            listOf(ToDoStatus.InProgress, ToDoStatus.Done)
    }
})
