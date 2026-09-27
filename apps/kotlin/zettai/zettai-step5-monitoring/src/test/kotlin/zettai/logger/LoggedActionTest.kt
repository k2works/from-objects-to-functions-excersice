package zettai.logger

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import zettai.fp.ContextReader
import zettai.fp.Failure
import zettai.fp.ListNotFound
import zettai.fp.Outcome
import zettai.fp.Success
import zettai.fp.ZettaiError
import zettai.json.toJsonObject

/**
 * 第 12 章の「ポートの型を変えずにログを足す」を検査する。
 *
 * logged は横断関心事として ContextReader を包むだけなので、
 * 戻り値の型も値も変わらないことがテストで固定されていないと、
 * 「型を変えていない」という記事の主張が確かめられない。
 */
class LoggedActionTest {
    private val lines = mutableListOf<String>()
    private val logger = jsonLogger(lines::add)

    /** 文脈は「接続がある状態」の代わり。ここでは単なる文字列で足りる。 */
    private val action: ContextReader<String, Int> = ContextReader { context -> context.length }

    @Test
    fun `包んでも結果は変わらない`() {
        val logged = action.logged(logger, LogContext("出来事を読む"))

        expectThat(logged.runWith("connection")).isEqualTo(action.runWith("connection"))
    }

    @Test
    fun `包むと 1 件のログが出る`() {
        action.logged(logger, LogContext("出来事を読む", mapOf("listName" to "book"))).runWith("connection")

        expectThat(lines).hasSize(1)

        val json = lines.single().toJsonObject()

        expectThat(json.text("operation")).isEqualTo("出来事を読む")
        expectThat(json.text("listName")).isEqualTo("book")
    }

    @Test
    fun `包まなければログは出ない`() {
        action.runWith("connection")

        expectThat(lines).hasSize(0)
    }

    @Test
    fun `実行するまでログは出ない`() {
        action.logged(logger, LogContext("出来事を読む"))

        expectThat(lines).hasSize(0)
    }

    @Test
    fun `包んだあとも map で繋げられる`() {
        val logged = action.logged(logger, LogContext("出来事を読む")).map { it * 2 }

        expectThat(logged.runWith("abc")).isEqualTo(6)
        expectThat(lines).hasSize(1)
    }

    @Test
    fun `失敗しうる計算は失敗を ERROR で記録する`() {
        val failing: ContextReader<String, Outcome<ZettaiError, Int>> =
            ContextReader { Failure(ListNotFound("book が見つかりません")) }

        val outcome = failing.loggedOutcome(logger, LogContext("出来事を読む")).runWith("connection")

        expectThat(outcome).isA<Failure<*>>()
        expectThat(lines.single().toJsonObject().text("level")).isEqualTo("ERROR")
    }

    @Test
    fun `失敗しうる計算は成功を INFO で記録する`() {
        val succeeding: ContextReader<String, Outcome<ZettaiError, Int>> = ContextReader { Success(1) }

        val outcome = succeeding.loggedOutcome(logger, LogContext("出来事を読む")).runWith("connection")

        expectThat(outcome).isEqualTo(Success(1))
        expectThat(lines.single().toJsonObject().text("level")).isEqualTo("INFO")
    }

    @Test
    fun `結果を読まない logged は失敗も INFO で記録する`() {
        val failing: ContextReader<String, Outcome<ZettaiError, Int>> =
            ContextReader { Failure(ListNotFound("なし")) }

        failing.logged(logger, LogContext("出来事を読む")).runWith("connection")

        // 結果の中身を知らないので「実行した」しか書けない。
        // 失敗を ERROR にしたいなら loggedOutcome を使う（第 12 章の制約）
        expectThat(lines.single().toJsonObject().text("level")).isEqualTo("INFO")
    }
}
