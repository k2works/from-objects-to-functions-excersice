package zettai.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import zettai.adapter.inmemory.inMemoryFetcher
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoList
import zettai.domain.User

class ToDoListHubTest : StringSpec({
    "constructor function は注入した fetcher を Hub の操作として公開する" {
        runTest {
            val user = User("uberto")
            val listName = ListName("book")
            val expected = ToDoList(listName, listOf(ToDoItem("write chapter")))
            val hub = todoListHub { actualUser, actualName ->
                if (actualUser == user && actualName == listName) expected else null
            }

            hub.getList(user, listName) shouldBe expected
            hub.getList(User("alice"), listName) shouldBe null
        }
    }

    "in-memory adapter は作成時の値を snapshot として保持する" {
        runTest {
            val user = User("uberto")
            val listName = ListName("book")
            val mutableItems = mutableListOf(ToDoItem("write chapter"))
            val mutableTodoLists = mutableListOf(ToDoList(listName, mutableItems))
            val mutableLists = mutableMapOf(
                user to mutableTodoLists,
            )
            val hub = todoListHub(inMemoryFetcher(mutableLists))

            mutableItems += ToDoItem("publish book")
            mutableTodoLists.clear()
            mutableLists.clear()

            hub.getList(user, listName)?.items shouldBe listOf(ToDoItem("write chapter"))
        }
    }
})
