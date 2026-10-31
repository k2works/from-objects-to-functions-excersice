package zettai.adapter.postgres

import arrow.core.Either
import java.sql.Connection
import java.sql.ResultSet
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zettai.application.EventStore
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoStatus
import zettai.domain.User
import zettai.domain.errors.PersistenceFailure
import zettai.domain.events.ItemAdded
import zettai.domain.events.ItemStatusChanged
import zettai.domain.events.ListCreated
import zettai.domain.events.ToDoListEvent

/** テーブルとインデックスを作る。何度呼んでも同じ結果になる。 */
suspend fun createSchema(connect: () -> Connection): Either<PersistenceFailure, Unit> =
    blocking {
        connect().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(EventTable.CREATE_TABLE)
                statement.execute(EventTable.CREATE_INDEX)
            }
        }
        Unit
    }

/**
 * 接続の作り方は外から関数で受け取る。blocking の JDBC は `Dispatchers.IO` に閉じ込め、
 * connection は操作ごとに開いて `use` で閉じる。JDBC の例外と壊れた行は `PersistenceFailure` にして返す。
 */
fun postgresEventStore(connect: () -> Connection): EventStore =
    EventStore(
        readAggregate = { user, listName ->
            query(connect, EventTable.SELECT_BY_ENTITY, entityId(user, listName))
        },
        readUser = { user ->
            query(connect, EventTable.SELECT_BY_USER, userPrefix(user))
        },
        persist = { events -> append(connect, events) },
    )

/**
 * blocking の呼び出しを IO 用の dispatcher で実行し、例外を `PersistenceFailure` にする。
 * `Either.catch` は `CancellationException` を捕まえずに投げ直すので、coroutine のキャンセルを握りつぶさない。
 */
private suspend fun <A> blocking(block: () -> A): Either<PersistenceFailure, A> =
    withContext(Dispatchers.IO) { Either.catch(block) }
        .mapLeft { PersistenceFailure("${it::class.simpleName}: ${it.message}") }

private suspend fun append(
    connect: () -> Connection,
    events: List<ToDoListEvent>,
): Either<PersistenceFailure, Unit> =
    blocking {
        connect().use { connection ->
            connection.autoCommit = false
            try {
                events.forEach { event -> connection.insert(event) }
                connection.commit()
            } catch (failure: Throwable) {
                // close に任せず、確定していない書き込みを明示的に取り消す。pool の接続でも前提が崩れない。
                runCatching { connection.rollback() }.onFailure(failure::addSuppressed)
                throw failure
            }
        }
    }

private fun Connection.insert(event: ToDoListEvent) {
    val sql = when (event) {
        is ListCreated -> EventTable.INSERT_LIST_CREATED
        is ItemAdded -> EventTable.INSERT_ITEM_ADDED
        is ItemStatusChanged -> EventTable.INSERT_ITEM_STATUS_CHANGED
    }
    prepareStatement(sql).use { statement ->
        statement.setString(1, entityId(event.user, event.listName))
        statement.setString(2, event.user.name)
        statement.setString(3, event.listName.name)
        when (event) {
            is ListCreated -> Unit
            is ItemAdded -> {
                statement.setString(4, event.item.description)
                statement.setString(5, event.item.dueDate?.toString())
                statement.setString(6, event.item.status.name)
            }
            is ItemStatusChanged -> {
                statement.setString(4, event.description)
                statement.setString(5, event.newStatus.name)
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

private fun ResultSet.toEvents(): List<ToDoListEvent> =
    buildList {
        while (next()) {
            add(decode())
        }
    }

/** 列が無い、未知の種類、不正な値は例外になり、`blocking` が `PersistenceFailure` にする。 */
private fun ResultSet.decode(): ToDoListEvent {
    val user = User(required("user_name"))
    val listName = ListName(required("list_name"))
    return when (val type = getString("event_type")) {
        "ListCreated" -> ListCreated(user, listName)
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
