package zettai.persistence

import java.sql.Connection
import zettai.domain.HubAction
import zettai.domain.TxContext
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

    /** 件数を数える。テストで「1 件も残っていない」ことを確かめるために使う。 */
    fun countAll(): DbAction<Int> =
        ContextReader { connection ->
            connection.prepareStatement("SELECT count(*) FROM ${EventTable.NAME}").use { statement ->
                statement.executeQuery().use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
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
 * 操作ごとにコミットされる（autoCommit）。
 */
fun <T> DbAction<T>.runOn(connect: () -> Connection): Outcome<ZettaiError, T> =
    try {
        connect().use { connection -> runWith(connection).asSuccess() }
    } catch (e: Exception) {
        PersistenceError("データベースの操作に失敗しました: ${e.message}").asFailure()
    }


/**
 * JDBC の接続を持つ文脈。
 *
 * ドメインの TxContext は中身が空で、何を持つかはアダプタが決める。
 * 接続という具体はこのパッケージに閉じ込める。
 */
data class JdbcContext(val connection: Connection) : TxContext

/**
 * DbAction を HubAction に持ち上げる。
 *
 * 文脈から接続を取り出して渡す。永続化の実装だけが接続を必要とし、
 * インメモリの実装は文脈に触らない。
 */
fun <T> DbAction<T>.asHubAction(): HubAction<T> =
    ContextReader { context ->
        require(context is JdbcContext) { "この操作は接続を必要とするが、文脈が JdbcContext ではない: $context" }

        runWith(context.connection)
    }

/** ハブの操作を 1 つのトランザクションとして実行する。 */
fun <T> HubAction<T>.runInTransaction(connect: () -> Connection): Outcome<ZettaiError, T> =
    try {
        connect().use { connection ->
            connection.autoCommit = false

            try {
                val result = runWith(JdbcContext(connection))
                connection.commit()
                result.asSuccess()
            } catch (e: Exception) {
                connection.rollback()
                throw e
            }
        }
    } catch (e: Exception) {
        PersistenceError("トランザクションが失敗しました: ${e.message}").asFailure()
    }
