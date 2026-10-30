package zettai.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ToDoListTest : StringSpec({
    "ユーザーごとに同名のリストを区別する" {
        val book = ListName("book")
        val uberto = User("uberto")
        val alice = User("alice")
        val lists = mapOf(
            uberto to listOf(ToDoList(book, listOf(ToDoItem("write chapter")))),
            alice to listOf(ToDoList(book, listOf(ToDoItem("read chapter")))),
        )

        findToDoList(lists, uberto, book)?.items shouldBe listOf(ToDoItem("write chapter"))
        findToDoList(lists, alice, book)?.items shouldBe listOf(ToDoItem("read chapter"))
    }
})
