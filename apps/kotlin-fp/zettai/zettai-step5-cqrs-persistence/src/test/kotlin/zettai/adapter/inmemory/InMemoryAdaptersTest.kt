package zettai.adapter.inmemory

import arrow.core.getOrElse
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.User
import zettai.domain.events.ItemAdded
import zettai.domain.events.ListCreated

class InMemoryAdaptersTest : StringSpec({
    val alice = User("alice")
    val bob = User("bob")
    val book = ListName("book")
    val shop = ListName("shop")

    "readAggregate は指定した集約のイベントだけを追記順に返す" {
        runTest {
            val store = inMemoryEventStore(
                listOf(
                    ListCreated(alice, book),
                    ListCreated(alice, shop),
                    ListCreated(bob, book),
                    ItemAdded(alice, book, ToDoItem("write chapter")),
                ),
            )

            store.readAggregate(alice, book).getOrElse { error(it.message) } shouldBe listOf(
                ListCreated(alice, book),
                ItemAdded(alice, book, ToDoItem("write chapter")),
            )
        }
    }

    "readUser は user のイベントだけを追記順に返す" {
        runTest {
            val store = inMemoryEventStore(
                listOf(
                    ListCreated(alice, book),
                    ListCreated(bob, book),
                    ListCreated(alice, shop),
                ),
            )

            store.readUser(alice).getOrElse { error(it.message) } shouldBe listOf(ListCreated(alice, book), ListCreated(alice, shop))
        }
    }

    "event store は初期値と追記 batch の可変参照を保持しない" {
        runTest {
            val initial = mutableListOf(ListCreated(alice, book))
            val store = inMemoryEventStore(initial)
            initial.clear()

            val batch = mutableListOf(ItemAdded(alice, book, ToDoItem("write chapter")))
            store.persist(batch)
            batch.clear()

            store.readAggregate(alice, book).getOrElse { error(it.message) } shouldBe listOf(
                ListCreated(alice, book),
                ItemAdded(alice, book, ToDoItem("write chapter")),
            )
        }
    }
})
