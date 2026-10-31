package zettai.logging

import arrow.core.right
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import zettai.json.ConversionFailure

private fun <T> Arb<T>.orNull(): Arb<T?> = Arb.choice(this, Arb.constant(null))

private val instants: Arb<Instant> =
    Arb.bind(Arb.long(0L..4_000_000_000L), Arb.int(0..999_999_999)) { seconds, nanos ->
        Instant.ofEpochSecond(seconds, nanos.toLong())
    }

private val arbRecord: Arb<LogRecord> = Arb.bind(
    instants,
    Arb.string(0..8),
    Arb.string(0..8),
    Arb.string(0..8),
    Arb.string(0..8).orNull(),
    Arb.string(0..8).orNull(),
    Arb.long(0L..100_000L),
) { at, kind, name, outcome, user, list, durationMs ->
    LogRecord(at, kind, name, outcome, user, list, durationMs)
}

class LogRecordTest : StringSpec({
    val valid = """{"at":"2026-11-01T00:00:00Z","kind":"command","name":"X","outcome":"success","durationMs":3}"""

    "任意の記録は、1 行の文字列にして読み戻すと同じ記録になる" {
        checkAll(arbRecord) { record ->
            logLine.decoder.run(logLine.encoder.run(record)) shouldBe record.right()
        }
    }

    "件数と path と status を持つ記録も往復する" {
        val record = LogRecord(
            at = Instant.parse("2026-11-01T00:00:00Z"),
            kind = "store",
            name = "append",
            outcome = "success",
            durationMs = 7,
            streams = 2,
            events = 3,
            path = "/todo/alice/book",
            status = 200,
        )

        logLine.decoder.run(logLine.encoder.run(record)) shouldBe record.right()
    }

    "必須の項目が欠けた行は、例外ではなく Left になる" {
        listOf("at", "kind", "name", "outcome", "durationMs").forEach { key ->
            val obj = Json.parseToJsonElement(valid) as JsonObject
            val without = Json.encodeToString(JsonObject.serializer(), JsonObject(obj - key))

            logLine.decoder.run(without).leftOrNull().shouldBeInstanceOf<ConversionFailure>()
        }
    }

    "オブジェクトでない JSON や、壊れた時刻は Left になる" {
        logLine.decoder.run("[1,2]").isLeft() shouldBe true
        logLine.decoder.run(valid.replace("2026-11-01T00:00:00Z", "いつか")).isLeft() shouldBe true
    }

    "項目があるのに型が違う行は、黙って null にせず Left になる" {
        logLine.decoder.run(valid.replace("\"durationMs\":3", "\"durationMs\":\"3\"")).isLeft() shouldBe true
        logLine.decoder.run(valid.replace("}", ",\"user\":5}")).isLeft() shouldBe true
        logLine.decoder.run(valid.replace("}", ",\"events\":\"many\"}")).isLeft() shouldBe true
    }

    "Int の範囲を超える件数は、黙って切り詰めず Left になる" {
        logLine.decoder.run(valid.replace("}", ",\"events\":2147483648}")).isLeft() shouldBe true
    }

    "読めなかった理由に、入力の値を含めない" {
        val broken = valid.replace("\"durationMs\":3", "\"durationMs\":\"password-secret-value\"")

        val failure = logLine.decoder.run(broken).leftOrNull().shouldBeInstanceOf<ConversionFailure>()

        failure.reason.contains("secret") shouldBe false
    }

    "記録の行に現れるキーは、許可した集合の中だけ" {
        val allowed = setOf(
            "at", "kind", "name", "outcome", "user", "list", "durationMs", "streams", "events", "path", "status",
        )
        val full = LogRecord(
            at = Instant.parse("2026-11-01T00:00:00Z"),
            kind = "k",
            name = "n",
            outcome = "o",
            user = "u",
            list = "l",
            durationMs = 1,
            streams = 1,
            events = 1,
            path = "/p",
            status = 200,
        )

        val keys = (Json.parseToJsonElement(logLine.encoder.run(full)) as JsonObject).keys

        keys shouldBe allowed
    }
})
