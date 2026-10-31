package zettai.adapter.postgres

import java.sql.Connection
import java.sql.ResultSet
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zettai.application.AggregateEventReader
import zettai.application.EventPersister
import zettai.application.UserEventReader
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoStatus
import zettai.domain.User
import zettai.domain.events.ItemAdded
import zettai.domain.events.ItemStatusChanged
import zettai.domain.events.ListCreated
import zettai.domain.events.ToDoListEvent

data class PostgresEventStore(
    val readAggregate: AggregateEventReader,
    val readUser: UserEventReader,
    val persist: EventPersister,
)

/** テーブルとインデックスを作る。何度呼んでも同じ結果になる。 */
suspend fun createSchema(connect: () -> Connection) {
    withContext(Dispatchers.IO) {
        connect().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(EventTable.CREATE_TABLE)
                statement.execute(EventTable.CREATE_INDEX)
            }
        }
    }
}

/**
 * 接続の作り方は外から関数で受け取る。blocking の JDBC は `Dispatchers.IO` に閉じ込め、
 * connection は操作ごとに開いて `use` で閉じる。
 */
fun postgresEventStore(connect: () -> Connection): PostgresEventStore =
    PostgresEventStore(
        readAggregate = { user, listName ->
            query(connect, EventTable.SELECT_BY_ENTITY, entityId(user, listName))
        },
        readUser = { user ->
            query(connect, EventTable.SELECT_BY_USER, userPrefix(user))
        },
        persist = { events -> append(connect, events) },
    )

private suspend fun append(connect: () -> Connection, events: List<ToDoListEvent>) {
    withContext(Dispatchers.IO) {
        connect().use { connection ->
            connection.autoCommit = false
            events.forEach { event -> connection.insert(event) }
            connection.commit()
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
): List<ToDoListEvent> =
    withContext(Dispatchers.IO) {
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

private fun ResultSet.decode(): ToDoListEvent {
    val user = User(getString(2))
    val listName = ListName(getString(3))
    return when (val type = getString(1)) {
        "ListCreated" -> ListCreated(user, listName)
        "ItemAdded" -> ItemAdded(
            user,
            listName,
            ToDoItem(
                description = getString(6),
                dueDate = getString(7)?.let(LocalDate::parse),
                status = ToDoStatus.valueOf(getString(8)),
            ),
        )
        "ItemStatusChanged" -> ItemStatusChanged(user, listName, getString(4), ToDoStatus.valueOf(getString(5)))
        else -> error("未知の event_type: $type")
    }
}
