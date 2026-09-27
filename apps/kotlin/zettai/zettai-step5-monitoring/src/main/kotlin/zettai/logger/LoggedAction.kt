package zettai.logger

import java.time.Instant
import zettai.fp.ContextReader
import zettai.fp.Outcome

/**
 * 文脈つきの計算に「実行した」というログを足す。
 *
 * **ポートの型を変えていない。** 包むだけなので、呼び出し側は変わらない。
 * 第 7 章と第 10 章ではポートの型を変えて 6 ファイル・7 変更 + 3 新設が影響したが、
 * ログは横断関心事なので包むだけで済む。
 *
 * ただし**結果の中身は読めない**。T が何かを知らないから型を変えずに包めたので、
 * 成功と失敗を書き分けたいなら T が Outcome であることを知る必要がある（loggedOutcome）。
 */
fun <CTX, T> ContextReader<CTX, T>.logged(logger: Logger, context: LogContext): ContextReader<CTX, T> =
    ContextReader { ctx ->
        val result = runWith(ctx)

        logger.log(LogSuccess(Instant.now(), "${context.operation} を実行しました", context))

        result
    }

/**
 * 失敗しうる計算に、成功と失敗を書き分けるログを足す。
 *
 * 戻り値の型は変わらない（包むだけ）。変わったのは**要求する型**で、
 * 中身が Outcome であることを知らないと失敗を ERROR として書けない。
 * 「横断関心事は包める」は、ログの内容が結果に依存しない範囲での話だった。
 */
fun <CTX, E, T> ContextReader<CTX, Outcome<E, T>>.loggedOutcome(
    logger: Logger,
    context: LogContext
): ContextReader<CTX, Outcome<E, T>> =
    ContextReader { ctx -> logger.logging(context) { runWith(ctx) } }
