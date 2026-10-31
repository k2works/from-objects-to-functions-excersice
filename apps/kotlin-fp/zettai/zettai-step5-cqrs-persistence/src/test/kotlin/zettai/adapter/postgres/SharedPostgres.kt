package zettai.adapter.postgres

import arrow.core.getOrElse
import java.sql.Connection
import java.sql.DriverManager
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * テストで共有する PostgreSQL。container は JVM で 1 つだけ起動し、終了時に Testcontainers が片付ける。
 * 全テストが同じテーブルを作り直すので、テストは逐次で実行する前提。並列に実行するなら spec ごとに schema を分ける。
 * 接続情報は container が公開した値だけを使い、固定のポートや認証情報を持たない。
 */
object SharedPostgres {
    private val container: PostgreSQLContainer by lazy {
        PostgreSQLContainer("postgres:18-alpine").also { it.start() }
    }

    fun connect(): Connection =
        DriverManager.getConnection(container.jdbcUrl, container.username, container.password)

    /** テスト間で状態を共有しないよう、テーブルごと作り直す。 */
    suspend fun reset() {
        connect().use { connection ->
            connection.createStatement().use { it.execute("DROP TABLE IF EXISTS todo_list_event") }
        }
        createSchema(::connect).getOrElse { error("スキーマの作成に失敗: ${it.reason}") }
    }
}
