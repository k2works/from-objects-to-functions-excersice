package zettai

import arrow.core.Either
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest

class FunctionalCoreTest : FunSpec({
    test("純粋関数は同じ入力に同じ出力を返す") {
        wordScore("Kotlin") shouldBe 6
        wordScore("Kotlin") shouldBe 6
    }

    test("Either は失敗を値として返す") {
        nonBlankName("") shouldBe Either.Left("名前は空にできません")
        nonBlankName("仕事") shouldBe Either.Right("仕事")
    }

    test("suspend 関数はテスト用 coroutine で決定的に実行できる") {
        runTest {
            loadGreeting { "こんにちは" } shouldBe "こんにちは"
        }
    }
})
