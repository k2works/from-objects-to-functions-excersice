package zettai.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import zettai.application.todoListHub
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoList

class ZettaiHttpAppTest : StringSpec({
    "HTTP adapter は注入した Hub を差し替えられる" {
        val found = zettaiHttpApp(todoListHub { _, listName ->
            ToDoList(listName, listOf(ToDoItem("write chapter")))
        })
        val missing = zettaiHttpApp(todoListHub { _, _ -> null })
        val request = Request(GET, "/todo/uberto/book")
        val response = found(request)

        response.status shouldBe Status.OK
        response.header("Content-Type") shouldBe "text/html; charset=utf-8"
        response.bodyString() shouldContain "write chapter"
        missing(request).status shouldBe Status.NOT_FOUND
    }

    "HTTP 経由でも項目を HTML escape する" {
        val app = zettaiHttpApp(todoListHub { _, listName ->
            ToDoList(listName, listOf(ToDoItem("write <chapter>")))
        })
        val html = app(Request(GET, "/todo/uberto/book")).bodyString()

        html shouldContain "write &lt;chapter&gt;"
        html shouldNotContain "write <chapter>"
    }
})
