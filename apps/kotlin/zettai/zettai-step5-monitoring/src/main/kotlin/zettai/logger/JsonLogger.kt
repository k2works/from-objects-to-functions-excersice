package zettai.logger

import zettai.json.JsonObject
import zettai.json.toJsonString

/**
 * ログを 1 行 1 JSON で書き出す。
 *
 * JSON にするのは、後から機械で集計するため。文字列を目で読む前提のログは、
 * 件数が増えると追えなくなる。
 */
fun jsonLogger(write: (String) -> Unit): Logger = Logger { entry -> write(entry.toJson().toJsonString()) }

/** 標準出力に書くログ。 */
fun stdoutLogger(): Logger = jsonLogger(::println)

private fun LogEntry.toJson(): JsonObject =
    JsonObject(
        mapOf(
            "at" to at.toString(),
            "level" to if (this is LogFailure) "ERROR" else "INFO",
            "message" to message,
            "operation" to context.operation,
            "reason" to (this as? LogFailure)?.reason
        ) + context.detail
    )
