package zettai.logger

import zettai.fp.ContextReader

/**
 * 文脈つきの計算にログを足す。
 *
 * **ポートの型を変えていない。** 包むだけなので、呼び出し側は変わらない。
 * 第 7 章と第 10 章ではポートの型を変えて 6 ファイル・10 ファイルが影響したが、
 * ログは横断関心事なので包むだけで済む。
 */
fun <CTX, T> ContextReader<CTX, T>.logged(logger: Logger, context: LogContext): ContextReader<CTX, T> =
    ContextReader { ctx ->
        val result = runWith(ctx)

        logger.log(LogSuccess(java.time.Instant.now(), "${context.operation} を実行しました", context))

        result
    }
