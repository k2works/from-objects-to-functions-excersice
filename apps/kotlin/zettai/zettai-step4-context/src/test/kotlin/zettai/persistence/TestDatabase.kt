package zettai.persistence

import java.sql.Connection
import java.sql.DriverManager

/**
 * 結合テスト用のデータベース接続。
 *
 * docker-compose.yml の zettai-db サービスに繋ぐ。
 * 接続できない場合、テストは skip ではなく失敗させる。skip にすると
 * 「DB が無いから通った」のか「実装が正しいから通った」のか区別できない。
 */
object TestDatabase {
    private const val URL = "jdbc:postgresql://localhost:5432/zettai"
    private const val USER = "zettai"
    private const val PASSWORD = "zettai"

    fun connect(): Connection = DriverManager.getConnection(URL, USER, PASSWORD)

    /** スキーマを用意し、データを空にする。テストごとに呼ぶ。 */
    fun reset() {
        PostgresEventStore.createSchema().flatMap { PostgresEventStore.truncate() }.runOn(::connect)
    }
}
