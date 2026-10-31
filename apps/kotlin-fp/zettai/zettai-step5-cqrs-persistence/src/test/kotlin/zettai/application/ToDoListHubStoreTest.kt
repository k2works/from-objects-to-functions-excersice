package zettai.application

import arrow.core.getOrElse
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.checkAll
import zettai.adapter.inmemory.inMemoryEventStore
import zettai.adapter.postgres.SharedPostgres
import zettai.adapter.postgres.postgresEventStore
import zettai.arbCommand
import zettai.arbEvents
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoStatus
import zettai.domain.User
import zettai.domain.commands.AddToDoItem
import zettai.domain.commands.ChangeItemStatus
import zettai.domain.commands.CreateToDoList
import zettai.domain.commands.handle
import zettai.domain.events.replayFrom
import zettai.domain.queries.ToDoListRow
import zettai.interleave

/** 実際の store を通して、command 側と query 側が別の経路で同じ内容を返すことを確かめる。 */
class ToDoListHubStoreTest : StringSpec({
    val alice = User("alice")
    val book = ListName("book")

    listOf("in-memory", "PostgreSQL").forEach { kind ->
        "$kind: command で書き込んだ結果を、getList と listRows が別の経路で同じ内容として返す" {
            val store = if (kind == "PostgreSQL") {
                SharedPostgres.reset()
                postgresEventStore(SharedPostgres::connect)
            } else {
                inMemoryEventStore()
            }
            val hub = todoListHub(store)

            hub.execute(CreateToDoList(alice, book)).getOrElse { error(it.message) }
            hub.execute(AddToDoItem(alice, book, ToDoItem("a"))).getOrElse { error(it.message) }
            hub.execute(AddToDoItem(alice, book, ToDoItem("b"))).getOrElse { error(it.message) }
            hub.execute(ChangeItemStatus(alice, book, "a", ToDoStatus.Done)).getOrElse { error(it.message) }

            val list = hub.getList(alice, book).getOrElse { error(it.message) }
            val rows = hub.listRows(alice).getOrElse { error(it.message) }

            list.items shouldBe listOf(ToDoItem("a", status = ToDoStatus.Done), ToDoItem("b"))
            rows shouldBe listOf(ToDoListRow(book, itemCount = 2, doneCount = 1))
        }
    }

    "他の集約のイベントを途中に混ぜても、command の判断は変わらない" {
        checkAll(arbEvents, arbEvents, arbCommand) { events, extra, command ->
            val own = events.filter { it.user == command.user && it.listName == command.listName }
            val foreign = extra.filter { it.user != command.user || it.listName != command.listName }

            handle(command, interleave(own, foreign).replayFrom()) shouldBe handle(command, own.replayFrom())
        }
    }
})
