package zettai.logging

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import java.time.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import zettai.json.ConversionFailure
import zettai.json.Converter
import zettai.json.Decoder
import zettai.json.Encoder
import zettai.json.jsonText

/**
 * 構造化ログの 1 行。載せるのは、操作の種類、結果の型、識別子（利用者名とリスト名）、所要時間、件数と、
 * request の method、path（query は含めない）、status だけ。
 * 項目の説明、event の payload、`PersistenceFailure.reason`、例外のスタックトレースは、この型に持ち場所が無い。
 * 載せないものを、書かない規律ではなく、型で表す。
 */
data class LogRecord(
    val at: Instant,
    val kind: String,
    val name: String,
    val outcome: String,
    val user: String? = null,
    val list: String? = null,
    val durationMs: Long,
    val streams: Int? = null,
    val events: Int? = null,
    val path: String? = null,
    val status: Int? = null,
)

private val logRecordJson: Converter<LogRecord, JsonElement> =
    Converter(
        encoder = Encoder { record ->
            JsonObject(
                buildMap {
                    put("at", JsonPrimitive(record.at.toString()))
                    put("kind", JsonPrimitive(record.kind))
                    put("name", JsonPrimitive(record.name))
                    put("outcome", JsonPrimitive(record.outcome))
                    record.user?.let { put("user", JsonPrimitive(it)) }
                    record.list?.let { put("list", JsonPrimitive(it)) }
                    put("durationMs", JsonPrimitive(record.durationMs))
                    record.streams?.let { put("streams", JsonPrimitive(it)) }
                    record.events?.let { put("events", JsonPrimitive(it)) }
                    record.path?.let { put("path", JsonPrimitive(it)) }
                    record.status?.let { put("status", JsonPrimitive(it)) }
                },
            )
        },
        decoder = Decoder { json -> either { decodeRecord(json) } },
    )

private fun Raise<ConversionFailure>.decodeRecord(json: JsonElement): LogRecord {
    val obj = ensureNotNull(json as? JsonObject) { ConversionFailure("オブジェクトではない") }

    fun primitive(key: String): JsonPrimitive? = obj[key] as? JsonPrimitive

    fun text(key: String): String =
        ensureNotNull(primitive(key)?.takeIf { it.isString }?.content) { ConversionFailure("$key が文字列ではない") }

    // キーが無ければ null。あるのに型が違えば、黙って null にせず失敗にする
    fun optionalText(key: String): String? =
        if (key in obj) text(key) else null

    fun number(key: String): Long =
        ensureNotNull(primitive(key)?.takeIf { !it.isString }?.longOrNull) { ConversionFailure("$key が整数ではない") }

    fun optionalNumber(key: String): Int? =
        if (key in obj) {
            ensureNotNull(number(key).takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()) {
                ConversionFailure("$key が Int の範囲を超えている")
            }
        } else {
            null
        }

    val atText = text("at")
    val at = Either.catch { Instant.parse(atText) }.getOrNull()
    return LogRecord(
        at = ensureNotNull(at) { ConversionFailure("at が時刻ではない") },
        kind = text("kind"),
        name = text("name"),
        outcome = text("outcome"),
        user = optionalText("user"),
        list = optionalText("list"),
        durationMs = number("durationMs"),
        streams = optionalNumber("streams"),
        events = optionalNumber("events"),
        path = optionalText("path"),
        status = optionalNumber("status"),
    )
}

/** [LogRecord] と、改行を含まない 1 行の JSON 文字列の双方向変換。 */
val logLine: Converter<LogRecord, String> = logRecordJson.andThen(jsonText)
