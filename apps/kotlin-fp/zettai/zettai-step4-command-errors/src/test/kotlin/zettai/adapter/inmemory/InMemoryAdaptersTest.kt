package zettai.adapter.inmemory

import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoList
import zettai.domain.User
import zettai.domain.errors.ListNotFound
import zettai.domain.events.ItemAdded
import zettai.domain.events.ListCreated

class InMemoryAdaptersTest : StringSpec({
    "fetcher は外部の可変 collection から snapshot を作る" {
        runTest {
            val user = User("uberto")
            val listName = ListName("book")
            val mutableItems = mutableListOf(ToDoItem("write chapter"))
            val mutableLists = mutableListOf(ToDoList(listName, mutableItems))
            val source = mutableMapOf(user to mutableLists)
            val fetch = inMemoryFetcher(source)

            mutableItems.clear()
            mutableLists.clear()
            source.clear()

            fetch(user, listName) shouldBe
                ToDoList(listName, listOf(ToDoItem("write chapter"))).right()
            fetch(user, ListName("missing")) shouldBe
                ListNotFound(user, ListName("missing")).left()
        }
    }

    "event store は初期値と追記 batch の可変参照を保持しない" {
        runTest {
            val user = User("uberto")
            val listName = ListName("book")
            val initial = mutableListOf(ListCreated(user, listName))
            val store = inMemoryEventStore(initial)
            initial.clear()

            val batch = mutableListOf(
                ItemAdded(user, listName, ToDoItem("write chapter")),
            )
            store.persist(batch)
            batch.clear()

            store.fetchState().listFor(user, listName)?.items shouldBe
                listOf(ToDoItem("write chapter"))
        }
    }
})
