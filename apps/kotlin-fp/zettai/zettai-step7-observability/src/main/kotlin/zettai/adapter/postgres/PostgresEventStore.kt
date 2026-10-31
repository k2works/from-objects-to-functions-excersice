package zettai.adapter.postgres

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zettai.application.EventStore
import zettai.application.StreamEvents
import zettai.domain.ListName
import zettai.domain.StreamId
import zettai.domain.ToDoItem
import zettai.domain.ToDoStatus
import zettai.domain.User
import zettai.domain.errors.ConcurrentModification
import zettai.domain.errors.PersistenceFailure
import zettai.domain.errors.StoreError
import zettai.domain.errors.ZettaiError
import zettai.domain.events.ItemAdded
import zettai.domain.events.ItemStatusChanged
import zettai.domain.events.ListCreated
import zettai.domain.events.ListRenamed
import zettai.domain.events.ToDoListEvent
import zettai.domain.events.streamId

/** PostgreSQL の一意制約の違反。同じ stream の同じ版への挿入が衝突したときに起きる。 */
private const val UNIQUE_VIOLATION = "23505"

/** テーブルとインデックスを作る。何度呼んでも同じ結果になる。 */
suspend fun createSchema(connect: () -> Connection): Either<PersistenceFailure, Unit> =
    blocking {
        connect().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(EventTable.CREATE_TABLE)
                statement.execute(EventTable.CREATE_INDEX)
            }
            check(connection.hasVersionColumn()) {
                "todo_list_event に version 列が無い。既存のテーブルは移行しないので、作り直す"
            }
        }
        Unit
    }

/**
 * 環境 [Connection] を transaction の中で渡す event store。
 * query は transaction の外で、操作ごとに connection を開いて `use` で閉じる。
 * blocking の JDBC は `Dispatchers.IO` に閉じ込め、JDBC の例外と壊れた行は `PersistenceFailure` にして返す。
 */
fun postgresEventStore(connect: () -> Connection): EventStore<Connection> =
    EventStore(
        readAggregate = { user, listName ->
            query(connect, EventTable.SELECT_BY_ENTITY, entityId(user, listName))
        },
        readUser = { user ->
            query(connect, EventTable.SELECT_BY_USER, userPrefix(user))
        },
        transaction = { block -> inTransaction(connect, block) },
        readStream = { connection, stream -> readStream(connection, stream) },
        append = { connection, expected, events -> appendTo(connection, expected, events) },
    )

/**
 * blocking の呼び出しを IO 用の dispatcher で実行し、例外を `PersistenceFailure` にする。
 * `Either.catch` は `CancellationException` を捕まえずに投げ直すので、coroutine のキャンセルを握りつぶさない。
 */
private suspend fun <A> blocking(block: () -> A): Either<PersistenceFailure, A> =
    withContext(Dispatchers.IO) { Either.catch(block) }
        .mapLeft { it.toFailure() }

private fun Throwable.toFailure() = PersistenceFailure("${this::class.simpleName}: $message")

/**
 * 1 つの transaction。block が `Right` を返したときだけ commit し、`Left` や例外のときは rollback する。
 * 境界は、この関数の中に 1 か所だけある。
 */
private suspend fun inTransaction(
    connect: () -> Connection,
    block: suspend (Connection) -> Either<ZettaiError, List<ToDoListEvent>>,
): Either<ZettaiError, List<ToDoListEvent>> =
    withContext(Dispatchers.IO) {
        Either.catch {
            connect().use { connection ->
                connection.autoCommit = false
                try {
                    val result = block(connection)
                    if (result.isRight()) connection.commit() else connection.rollback()
                    result
                } catch (failure: Throwable) {
                    runCatching { connection.rollback() }.onFailure(failure::addSuppressed)
                    throw failure
                }
            }
        }.fold({ it.toFailure().left() }, { it })
    }

private fun Connection.hasVersionColumn(): Boolean =
    prepareStatement(EventTable.HAS_VERSION_COLUMN).use { statement ->
        statement.executeQuery().use { rows -> rows.next() }
    }

private fun readStream(connection: Connection, stream: StreamId): Either<PersistenceFailure, StreamEvents> =
    Either.catch {
        connection.prepareStatement(EventTable.SELECT_BY_ENTITY).use { statement ->
            statement.setString(1, entityId(stream.user, stream.listName))
            statement.executeQuery().use { rows -> rows.toStreamEvents() }
        }
    }.mapLeft { it.toFailure() }

/**
 * event を入力の順に、stream ごとに、読んだ版の次から連番を付けて挿入する。
 * 一意制約の違反は `ConcurrentModification` にする。呼ぶ側の transaction が、失敗したとき全体を rollback する。
 */
private fun appendTo(
    connection: Connection,
    expected: Map<StreamId, Int>,
    events: List<ToDoListEvent>,
): Either<StoreError, Unit> {
    val appended = mutableMapOf<StreamId, Int>()
    for (event in events) {
        val stream = event.streamId()
        val base = expected[stream] ?: return PersistenceFailure("期待する版が無い stream: $stream").left()
        val version = base + (appended.merge(stream, 1, Int::plus) ?: 1)
        try {
            connection.insert(event, version)
        } catch (failure: SQLException) {
            return if (failure.sqlState == UNIQUE_VIOLATION) {
                ConcurrentModification(listOf(stream)).left()
            } else {
                failure.toFailure().left()
            }
        }
    }
    return Unit.right()
}

private fun Connection.insert(event: ToDoListEvent, version: Int) {
    val sql = when (event) {
        is ListCreated -> EventTable.INSERT_LIST_CREATED
        is ListRenamed -> EventTable.INSERT_LIST_RENAMED
        is ItemAdded -> EventTable.INSERT_ITEM_ADDED
        is ItemStatusChanged -> EventTable.INSERT_ITEM_STATUS_CHANGED
    }
    prepareStatement(sql).use { statement ->
        statement.setString(1, entityId(event.user, event.listName))
        statement.setInt(2, version)
        statement.setString(3, event.user.name)
        statement.setString(4, event.listName.name)
        when (event) {
            is ListCreated -> Unit
            is ListRenamed -> statement.setString(5, event.newName.name)
            is ItemAdded -> {
                statement.setString(5, event.item.description)
                statement.setString(6, event.item.dueDate?.toString())
                statement.setString(7, event.item.status.name)
            }
            is ItemStatusChanged -> {
                statement.setString(5, event.description)
                statement.setString(6, event.newStatus.name)
            }
        }
        statement.executeUpdate()
    }
}

private suspend fun query(
    connect: () -> Connection,
    sql: String,
    key: String,
): Either<PersistenceFailure, List<ToDoListEvent>> =
    blocking {
        connect().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, key)
                statement.executeQuery().use { rows -> rows.toEvents() }
            }
        }
    }

/** 版は行の件数ではなく、`version` 列の最大値。追記は、この版の次から連番を付ける。 */
private fun ResultSet.toStreamEvents(): StreamEvents {
    val events = mutableListOf<ToDoListEvent>()
    var version = 0
    while (next()) {
        events.add(decode())
        version = maxOf(version, getInt("version"))
    }
    return StreamEvents(events, version)
}

private fun ResultSet.toEvents(): List<ToDoListEvent> =
    buildList {
        while (next()) {
            add(decode())
        }
    }

/** 列が無い、未知の種類、不正な値は例外になり、呼び出し側が `PersistenceFailure` にする。 */
private fun ResultSet.decode(): ToDoListEvent {
    val user = User(required("user_name"))
    val listName = ListName(required("list_name"))
    return when (val type = getString("event_type")) {
        "ListCreated" -> ListCreated(user, listName)
        "ListRenamed" -> ListRenamed(user, listName, ListName(required("new_name")))
        "ItemAdded" -> ItemAdded(
            user,
            listName,
            ToDoItem(
                description = required("item_description"),
                dueDate = getString("item_due_date")?.let(LocalDate::parse),
                status = ToDoStatus.valueOf(required("item_status")),
            ),
        )
        "ItemStatusChanged" -> ItemStatusChanged(
            user,
            listName,
            required("description"),
            ToDoStatus.valueOf(required("new_status")),
        )
        else -> error("未知の event_type: $type")
    }
}

private fun ResultSet.required(column: String): String =
    requireNotNull(getString(column)) { "payload に $column が無い" }
