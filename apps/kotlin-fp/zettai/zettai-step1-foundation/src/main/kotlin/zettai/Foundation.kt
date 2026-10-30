package zettai

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.runBlocking
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status

fun wordScore(word: String): Int = word.length

fun nonBlankName(name: String): Either<String, String> =
    if (name.isBlank()) "名前は空にできません".left() else name.right()

suspend fun loadGreeting(load: suspend () -> String): String = load()

fun effectfulHandler(load: suspend () -> String): HttpHandler = {
    Response(Status.OK).body(runBlocking { load() })
}

fun scoreHandler(): HttpHandler = { request ->
    val segments = request.uri.path.trim('/').split('/')
    if (request.method == Method.GET && segments.size == 2 && segments[0] == "score") {
        Response(Status.OK).body(wordScore(segments[1]).toString())
    } else {
        Response(Status.NOT_FOUND)
    }
}
