package zettai.web

import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoList
import zettai.domain.User

/**
 * 第 2 章の受け入れテスト。
 *
 * HTTP のリクエストから HTML までの縦串を、外側から確かめる。
 */
class SeeATodoListTest {

    private val listName = ListName("book")
    private val user = User("uberto")
    private val items = listOf("write chapter", "insert code", "publish book")

    private val zettai = Zettai(
        inMemoryFetcher(mapOf(user to listOf(ToDoList(listName, items.map(::ToDoItem)))))
    )

    @Test
    fun `ToDo リストの項目が HTML に含まれる`() {
        val response = zettai(Request(Method.GET, "/todo/uberto/book"))

        expectThat(response.status).isEqualTo(Status.OK)
        items.forEach { expectThat(response.bodyString()).contains(it) }
    }

    @Test
    fun `存在しないリストは 404 を返す`() {
        val response = zettai(Request(Method.GET, "/todo/uberto/missing"))

        expectThat(response.status).isEqualTo(Status.NOT_FOUND)
    }
}
