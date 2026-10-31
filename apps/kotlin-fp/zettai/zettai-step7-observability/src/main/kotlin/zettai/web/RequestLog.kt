package zettai.web

import java.time.Duration
import java.time.Instant
import org.http4k.core.Filter
import org.http4k.core.Status
import zettai.logging.LogRecord
import zettai.logging.logLine

/**
 * request ごとに 1 行のログを出す。method、path、status、所要時間だけを載せ、query と body、header は載せない。
 * `CatchAll` より外に付けるので、予期しない例外も 500 の response として記録される。
 */
fun requestLogged(log: (String) -> Unit, now: () -> Instant): Filter =
    Filter { next ->
        { request ->
            val startedAt = now()
            val response = next(request)
            val finishedAt = now()
            log(
                logLine.encoder.run(
                    LogRecord(
                        at = startedAt,
                        kind = "request",
                        name = request.method.name,
                        outcome = outcomeOf(response.status),
                        durationMs = Duration.between(startedAt, finishedAt).toMillis(),
                        path = request.uri.path,
                        status = response.status.code,
                    ),
                ),
            )
            response
        }
    }

private fun outcomeOf(status: Status): String =
    when {
        status.serverError -> "serverError"
        status.clientError -> "clientError"
        else -> "success"
    }
