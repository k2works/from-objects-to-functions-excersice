package zettai.domain.queries

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoStatus
import zettai.domain.User
import zettai.domain.events.ItemAdded
import zettai.domain.events.ItemStatusChanged
import zettai.domain.events.ListCreated
import zettai.domain.events.ToDoListEvent

class ProjectionTest : StringSpec({
    val alice = User("alice")
    val bob = User("bob")
    val book = ListName("book")
    val shop = ListName("shop")

    fun item(description: String) = ToDoItem(description)

    data class Case(
        val name: String,
        val events: List<ToDoListEvent>,
        val rows: List<ToDoListRow>,
    )

    listOf(
        Case("イベントがなければ行もない", emptyList(), emptyList()),
        Case(
            "作成しただけのリストは項目 0 件",
            listOf(ListCreated(alice, book)),
            listOf(ToDoListRow(book, 0, 0)),
        ),
        Case(
            "項目数と完了数を数える",
            listOf(
                ListCreated(alice, book),
                ItemAdded(alice, book, item("a")),
                ItemAdded(alice, book, item("b")),
                ItemStatusChanged(alice, book, "a", ToDoStatus.Done),
            ),
            listOf(ToDoListRow(book, 2, 1)),
        ),
        Case(
            "複数のリストは作成順に並ぶ",
            listOf(ListCreated(alice, shop), ListCreated(alice, book)),
            listOf(ToDoListRow(shop, 0, 0), ToDoListRow(book, 0, 0)),
        ),
        Case(
            "他のユーザーのイベントは含めない",
            listOf(ListCreated(alice, book), ListCreated(bob, shop), ItemAdded(bob, shop, item("x"))),
            listOf(ToDoListRow(book, 0, 0)),
        ),
        Case(
            "未作成のリストへの項目追加は無視する",
            listOf(ItemAdded(alice, book, item("a"))),
            emptyList(),
        ),
        Case(
            "存在しない項目の状態変更は無視する",
            listOf(
                ListCreated(alice, book),
                ItemAdded(alice, book, item("a")),
                ItemStatusChanged(alice, book, "missing", ToDoStatus.Done),
            ),
            listOf(ToDoListRow(book, 1, 0)),
        ),
        Case(
            "重複した ListCreated は項目を空に戻す",
            listOf(ListCreated(alice, book), ItemAdded(alice, book, item("a")), ListCreated(alice, book)),
            listOf(ToDoListRow(book, 0, 0)),
        ),
        Case(
            "同じ説明の項目が複数あっても状態変更は最初の項目だけに効く",
            listOf(
                ListCreated(alice, book),
                ItemAdded(alice, book, item("a")),
                ItemAdded(alice, book, item("a")),
                ItemStatusChanged(alice, book, "a", ToDoStatus.Done),
            ),
            listOf(ToDoListRow(book, 2, 1)),
        ),
    ).forEach { case ->
        case.name {
            projectFrom(alice, case.events) shouldBe case.rows
        }
    }
})
