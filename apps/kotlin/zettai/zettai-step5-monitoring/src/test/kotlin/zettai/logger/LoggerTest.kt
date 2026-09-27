package zettai.logger

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import zettai.fp.Failure
import zettai.fp.ListNotFound
import zettai.fp.Success
import zettai.json.toJsonObject

class LoggerTest {
    private val lines = mutableListOf<String>()
    private val logger = jsonLogger(lines::add)

    @Test
    fun `成功を記録する`() {
        logger.logging(LogContext("リストを作る")) { Success(1) }

        expectThat(lines).hasSize(1)
        expectThat(lines.single().toJsonObject().text("level")).isEqualTo("INFO")
    }

    @Test
    fun `失敗を記録する`() {
        logger.logging(LogContext("リストを取る")) { Failure(ListNotFound("book が見つかりません")) }

        val json = lines.single().toJsonObject()

        expectThat(json.text("level")).isEqualTo("ERROR")
        expectThat(json.text("reason")).contains("book が見つかりません")
    }

    @Test
    fun `結果はそのまま返る`() {
        val outcome = logger.logging(LogContext("何か")) { Success("値") }

        expectThat(outcome).isEqualTo(Success("値"))
    }

    @Test
    fun `失敗もそのまま返る`() {
        val outcome = logger.logging(LogContext("何か")) { Failure(ListNotFound("なし")) }

        expectThat(outcome).isA<Failure<*>>()
    }

    @Test
    fun `1 行 1 JSON で出力される`() {
        logger.logging(LogContext("1 つめ")) { Success(1) }
        logger.logging(LogContext("2 つめ")) { Success(2) }

        expectThat(lines).hasSize(2)
        lines.forEach { line ->
            expectThat(line.contains("\n")).isEqualTo(false)
            expectThat(line.toJsonObject().fields.isNotEmpty()).isEqualTo(true)
        }
    }

    @Test
    fun `文脈の詳細も出力される`() {
        logger.logging(LogContext("リストを作る", mapOf("user" to "uberto", "listName" to "book"))) { Success(1) }

        val json = lines.single().toJsonObject()

        expectThat(json.text("user")).isEqualTo("uberto")
        expectThat(json.text("listName")).isEqualTo("book")
        expectThat(json.text("operation")).isEqualTo("リストを作る")
    }
}
