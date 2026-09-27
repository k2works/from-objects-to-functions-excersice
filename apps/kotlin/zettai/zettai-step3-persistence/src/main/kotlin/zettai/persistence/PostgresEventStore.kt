package zettai.persistence

import java.sql.Connection
import zettai.domain.events.ToDoListEvent
import zettai.fp.ContextReader
import zettai.fp.Outcome
import zettai.fp.PersistenceError
import zettai.fp.ZettaiError
import zettai.fp.asFailure
import zettai.fp.asSuccess

/** 接続がある状態で実行できる計算。 */
typealias DbAction<T> = ContextReader<Connection, T>

/**
 * PostgreSQL のイベントストア。
 *
 * ORM を使っていない。テーブルが 1 つで、操作が追記と読み出しの 2 つだけなので、
 * ORM が解決する問題（関連の読み込み、変更の追跡）が起きない。
 */
object PostgresEventStore {
    /** テーブルを用意する。追記のみなのでマイグレーションツールを入れていない。 */
    fun createSchema(): DbAction<Unit> =
        ContextReader { connection ->
            connection.createStatement().use { statement ->
                statement.execute(EventTable.CREATE)
                statement.execute(EventTable.CREATE_INDEX)
            }
        }

    /** すべての出来事を消す。テスト用。 */
    fun truncate(): DbAction<Unit> =
        ContextReader { connection ->
            connection.createStatement().use { it.execute("TRUNCATE ${EventTable.NAME}") }
        }

    fun append(entityId: EntityId, events: List<ToDoListEvent>): DbAction<Unit> =
        ContextReader { connection ->
            connection.prepareStatement(EventTable.INSERT).use { statement ->
                events.forEach { event ->
                    statement.setString(1, entityId.value)
                    statement.setString(2, event::class.simpleName)
                    statement.setString(3, event.toJson())
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }

    fun readAll(): DbAction<List<ToDoListEvent>> =
        ContextReader { connection ->
            connection.prepareStatement(EventTable.SELECT_ALL).use { statement ->
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            add(eventFrom(rows.getString("event_type"), rows.getString("payload")))
                        }
                    }
                }
            }
        }
}

/**
 * 接続を用意して計算を実行する。
 *
 * 失敗を Outcome に載せる。例外を投げないので、呼び出し側が失敗を無視できない。
 */
fun <T> DbAction<T>.runOn(connect: () -> Connection): Outcome<ZettaiError, T> =
    try {
        connect().use { connection -> runWith(connection).asSuccess() }
    } catch (e: Exception) {
        PersistenceError("データベースの操作に失敗しました: ${e.message}").asFailure()
    }
