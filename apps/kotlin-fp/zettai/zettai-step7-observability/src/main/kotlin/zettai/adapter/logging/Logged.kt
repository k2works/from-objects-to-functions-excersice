package zettai.adapter.logging

import arrow.core.Either
import java.time.Duration
import java.time.Instant
import zettai.application.EventStore
import zettai.application.ToDoListHub
import zettai.domain.ListName
import zettai.domain.User
import zettai.domain.errors.ZettaiError
import zettai.logging.LogRecord
import zettai.logging.logLine

/**
 * 操作を実行し、結果にかかわらず 1 行のログを 1 回の呼び出しで書く。結果はそのまま返す。
 * 時刻と出力先は引数で与えるので、テストは決定的に書ける。例外は記録せず、そのまま投げ直す。
 */
private suspend fun <E : ZettaiError, A> observe(
    log: (String) -> Unit,
    now: () -> Instant,
    kind: String,
    name: String,
    user: User?,
    list: ListName?,
    streams: Int? = null,
    events: (A) -> Int? = { null },
    operation: suspend () -> Either<E, A>,
): Either<E, A> {
    val startedAt = now()
    val result = operation()
    val finishedAt = now()
    val record = LogRecord(
        at = startedAt,
        kind = kind,
        name = name,
        outcome = result.fold({ it::class.simpleName ?: "Error" }, { "success" }),
        user = user?.name,
        list = list?.name,
        durationMs = Duration.between(startedAt, finishedAt).toMillis(),
        streams = streams,
        events = result.getOrNull()?.let(events),
    )
    log(logLine.encoder.run(record))
    return result
}

/** Hub の 3 つの操作（command、2 つの query）を包む。domain も application も、この decorator を知らない。 */
fun ToDoListHub.logged(log: (String) -> Unit, now: () -> Instant): ToDoListHub {
    val inner = this
    return ToDoListHub(
        getList = { user, listName ->
            observe(log, now, "query", "getList", user, listName) { inner.getList(user, listName) }
        },
        listRows = { user ->
            observe(log, now, "query", "listRows", user, null) { inner.listRows(user) }
        },
        execute = { command ->
            observe(
                log,
                now,
                "command",
                command::class.simpleName ?: "Command",
                command.user,
                command.listName,
                events = { it.size },
            ) { inner.execute(command) }
        },
    )
}

/**
 * store の読み出しと追記を包む。`transaction` は包まない（境界の意味を変えない）。
 * event の件数と stream の数だけを記録し、payload は載せない。
 */
fun <Tx> EventStore<Tx>.logged(log: (String) -> Unit, now: () -> Instant): EventStore<Tx> {
    val inner = this
    return copy(
        readAggregate = { user, listName ->
            observe(log, now, "store", "readAggregate", user, listName, events = { it.size }) {
                inner.readAggregate(user, listName)
            }
        },
        readUser = { user ->
            observe(log, now, "store", "readUser", user, null, events = { it.size }) { inner.readUser(user) }
        },
        readStream = { tx, stream ->
            observe(log, now, "store", "readStream", stream.user, stream.listName, events = { it.events.size }) {
                inner.readStream(tx, stream)
            }
        },
        append = { tx, versions, events ->
            val first = versions.keys.firstOrNull()
            observe(
                log,
                now,
                "store",
                "append",
                first?.user,
                first?.listName,
                streams = versions.size,
                events = { events.size },
            ) { inner.append(tx, versions, events) }
        },
    )
}
