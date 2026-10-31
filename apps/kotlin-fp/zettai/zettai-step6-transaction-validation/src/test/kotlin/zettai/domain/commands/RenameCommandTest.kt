package zettai.domain.commands

import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import zettai.domain.ListName
import zettai.domain.StreamId
import zettai.domain.ToDoItem
import zettai.domain.ToDoList
import zettai.domain.ToDoStatus
import zettai.domain.User
import zettai.domain.errors.ListAlreadyExists
import zettai.domain.errors.ListNotFound
import zettai.domain.events.ItemAdded
import zettai.domain.events.ItemStatusChanged
import zettai.domain.events.ListCreated
import zettai.domain.events.ListRenamed
import zettai.domain.events.ToDoListEvent
import zettai.domain.events.replayFrom

class RenameCommandTest : StringSpec({
    val user = User("alice")
    val book = ListName("book")
    val novel = ListName("novel")
    val draft = ToDoItem("draft", LocalDate.of(2026, 11, 1), ToDoStatus.InProgress)
    val done = ToDoItem("publish", null, ToDoStatus.Done)

    fun stateOf(vararg events: ToDoListEvent) = events.toList().replayFrom()

    val bookWithItems = arrayOf(
        ListCreated(user, book),
        ItemAdded(user, book, ToDoItem("draft", LocalDate.of(2026, 11, 1))),
        ItemStatusChanged(user, book, "draft", ToDoStatus.InProgress),
        ItemAdded(user, book, ToDoItem("publish")),
        ItemStatusChanged(user, book, "publish", ToDoStatus.InProgress),
        ItemStatusChanged(user, book, "publish", ToDoStatus.Done),
    )

    "command が触れる stream は、旧い名前と新しい名前の 2 つ" {
        RenameToDoList(user, book, novel).streams shouldBe listOf(StreamId(user, book), StreamId(user, novel))
    }

    "名前を変えると、旧い stream に ListRenamed、新しい stream に作成と項目の引き継ぎを返す" {
        val result = handle(RenameToDoList(user, book, novel), stateOf(*bookWithItems))

        result shouldBe listOf(
            ListRenamed(user, book, novel),
            ListCreated(user, novel),
            ItemAdded(user, novel, draft),
            ItemAdded(user, novel, done),
        ).right()
    }

    "元のリストが無ければ ListNotFound" {
        handle(RenameToDoList(user, book, novel), stateOf()) shouldBe ListNotFound(user, book).left()
    }

    "変名先がすでにあれば ListAlreadyExists" {
        val state = stateOf(*bookWithItems, ListCreated(user, novel))

        handle(RenameToDoList(user, book, novel), state) shouldBe ListAlreadyExists(user, novel).left()
    }

    "同じ名前への変更は、変名先がすでにあるので拒否される" {
        handle(RenameToDoList(user, book, book), stateOf(*bookWithItems)) shouldBe
            ListAlreadyExists(user, book).left()
    }

    "変名の event を replay すると、旧い名前は無くなり、新しい名前が項目を引き継ぐ" {
        val renamed = handle(RenameToDoList(user, book, novel), stateOf(*bookWithItems))
            .fold({ error("拒否された") }, { it })

        val state = (bookWithItems.toList() + renamed).replayFrom()

        state.listFor(user, book) shouldBe null
        state.listFor(user, novel) shouldBe ToDoList(novel, listOf(draft, done))
    }

    "名前を変えて戻しても、項目が二重にならない" {
        val first = handle(RenameToDoList(user, book, novel), stateOf(*bookWithItems))
            .fold({ error("拒否された") }, { it })
        val afterFirst = bookWithItems.toList() + first
        val back = handle(RenameToDoList(user, novel, book), afterFirst.replayFrom())
            .fold({ error("拒否された") }, { it })

        val state = (afterFirst + back).replayFrom()

        state.listFor(user, novel) shouldBe null
        state.listFor(user, book) shouldBe ToDoList(book, listOf(draft, done))
    }
})
