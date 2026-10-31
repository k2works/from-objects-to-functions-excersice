package zettai.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.runBlocking
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import zettai.adapter.inmemory.inMemoryEventStore
import zettai.application.ToDoListHub
import zettai.application.todoListHub
import zettai.domain.ListName
import zettai.domain.User
import zettai.domain.commands.CreateToDoList

class RenameHttpTest : StringSpec({
    val alice = User("alice")

    fun appWithBook(): Pair<ToDoListHub, org.http4k.core.HttpHandler> {
        val hub = todoListHub(inMemoryEventStore())
        runBlocking { hub.execute(CreateToDoList(alice, ListName("book"))) }
        return hub to zettaiHttpApp(hub)
    }

    fun post(newName: String) =
        Request(POST, "/todo/alice/book/rename")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("newName=" + java.net.URLEncoder.encode(newName, Charsets.UTF_8))

    "リスト画面から名前を変える画面への link がある" {
        val (_, app) = appWithBook()

        app(Request(GET, "/todo/alice/book")).bodyString() shouldContain "href=\"/todo/alice/book/rename\""
    }

    "名前を変える画面は、いまの名前を入力欄に入れて表示する" {
        val (_, app) = appWithBook()

        val response = app(Request(GET, "/todo/alice/book/rename"))

        response.status shouldBe Status.OK
        response.bodyString() shouldContain "name=\"newName\" value=\"book\""
        response.bodyString() shouldNotContain "{{"
    }

    "リストが無ければ、名前を変える画面は 404" {
        val (_, app) = appWithBook()

        app(Request(GET, "/todo/alice/missing/rename")).status shouldBe Status.NOT_FOUND
    }

    "成功は 302 で、百分率符号化した新しい名前の画面へ移る" {
        val (_, app) = appWithBook()

        val response = app(post("新 しい&名前"))

        response.status shouldBe Status.FOUND
        response.header("Location") shouldBe "/todo/alice/%E6%96%B0%20%E3%81%97%E3%81%84%26%E5%90%8D%E5%89%8D"
    }

    "検証の誤りは 400 で、すべての理由を並べ、入力を HTML エスケープして保持する" {
        val (_, app) = appWithBook()

        val response = app(post("<b>/" + "x".repeat(40)))

        response.status shouldBe Status.BAD_REQUEST
        response.bodyString() shouldContain "value=\"&lt;b&gt;/${"x".repeat(40)}\""
        response.bodyString() shouldContain "<li class=\"problem\">新しい名前は 40 文字以内にしてください（現在 44 文字）</li>"
        response.bodyString() shouldContain "<li class=\"problem\">新しい名前に使えない文字が含まれています（「/」）</li>"
        response.bodyString() shouldNotContain "<b>/"
    }

    "変名先がすでにあれば 400、元が無ければ 404" {
        val (hub, app) = appWithBook()
        runBlocking { hub.execute(CreateToDoList(alice, ListName("novel"))) }

        app(post("novel")).status shouldBe Status.BAD_REQUEST
        app(Request(POST, "/todo/alice/missing/rename")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("newName=x")).status shouldBe Status.NOT_FOUND
    }

    "予期しない例外の本文に、内部の情報を出さない" {
        val hub = ToDoListHub(
            getList = { _, _ -> throw IllegalStateException("jdbc:postgresql://db.internal:5432/zettai password=secret") },
            listRows = { error("使わない") },
            execute = { error("使わない") },
        )

        val response = zettaiHttpApp(hub)(Request(GET, "/todo/alice/book"))

        response.status shouldBe Status.INTERNAL_SERVER_ERROR
        response.bodyString() shouldBe "Internal server error"
        response.bodyString() shouldNotContain "secret"
        response.bodyString() shouldNotContain "db.internal"
    }
})
