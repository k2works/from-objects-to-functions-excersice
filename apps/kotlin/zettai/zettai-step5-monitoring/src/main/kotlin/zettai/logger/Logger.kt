package zettai.logger

import java.time.Instant
import zettai.fp.Outcome

/** ログを書き出す。出力先はアダプタが決める。 */
fun interface Logger {
    fun log(entry: LogEntry)
}

/** 何も書かないログ。テストで使う。 */
val silentLogger = Logger { }

/**
 * 操作を実行し、結果をログに残す。
 *
 * 成功と失敗で別の型を書くので、集計するときに文字列を解析しなくてよい。
 */
fun <E, T> Logger.logging(context: LogContext, action: () -> Outcome<E, T>): Outcome<E, T> {
    val outcome = action()

    log(
        outcome.fold(
            { LogFailure(Instant.now(), "${context.operation} が失敗しました", context, it.toString()) },
            { LogSuccess(Instant.now(), "${context.operation} が成功しました", context) }
        )
    )

    return outcome
}
