package zettai.logger

import java.time.Instant

/**
 * ログの 1 件。
 *
 * ログはドメインの外側に置く。ドメインは「記録する」ことを知らない。
 * 成功と失敗を型で分けるので、集計するときに文字列を解析しなくてよい。
 */
sealed interface LogEntry {
    val at: Instant
    val message: String
    val context: LogContext
}

data class LogSuccess(
    override val at: Instant,
    override val message: String,
    override val context: LogContext
) : LogEntry

data class LogFailure(
    override val at: Instant,
    override val message: String,
    override val context: LogContext,
    val reason: String
) : LogEntry

/** ログに添える文脈。何の処理の中で起きたかを示す。 */
data class LogContext(val operation: String, val detail: Map<String, String> = emptyMap())
