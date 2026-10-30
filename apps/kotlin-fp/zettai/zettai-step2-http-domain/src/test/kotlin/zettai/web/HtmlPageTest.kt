package zettai.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoList

class HtmlPageTest : StringSpec({
    "ドメイン値を HTML escape する" {
        val list = ToDoList(
            ListName("<book & notes>"),
            listOf(ToDoItem("read \"FP\" <today>")),
        )

        renderHtml(list) shouldContain "&lt;book &amp; notes&gt;"
        renderHtml(list) shouldContain "read &quot;FP&quot; &lt;today&gt;"
    }
})
