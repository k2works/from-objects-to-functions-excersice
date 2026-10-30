package zettai.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import zettai.application.inMemoryFetcher
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoList
import zettai.domain.User

class ZettaiHttpAppTest : StringSpec({
    val uberto = User("uberto")
    val book = ToDoList(
        ListName("book"),
        listOf(ToDoItem("write chapter"), ToDoItem("publish book")),
    )
    val app = zettaiHttpApp(inMemoryFetcher(mapOf(uberto to listOf(book))))

    "GET /todo/{user}/{list} はリストを HTML で返す" {
        val response = app(Request(GET, "/todo/uberto/book"))

        response.status shouldBe Status.OK
        response.header("Content-Type") shouldBe "text/html; charset=utf-8"
        response.bodyString() shouldContain "<h2>book</h2>"
        response.bodyString() shouldContain "<li>write chapter</li>"
        response.bodyString() shouldContain "<li>publish book</li>"
    }

    "存在しないリストは 404 を返す" {
        app(Request(GET, "/todo/uberto/missing")).status shouldBe Status.NOT_FOUND
    }

    "項目を HTTP レスポンスで HTML escape する" {
        val unsafe = ToDoList(
            ListName("unsafe"),
            listOf(ToDoItem("read \"FP\" <today>")),
        )
        val response = zettaiHttpApp(inMemoryFetcher(mapOf(uberto to listOf(unsafe))))(
            Request(GET, "/todo/uberto/unsafe"),
        )

        response.status shouldBe Status.OK
        response.bodyString() shouldContain "read &quot;FP&quot; &lt;today&gt;"
    }
})
