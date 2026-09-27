package zettai.fp

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class OutcomeTest {

    private val error = ListNotFound("book が見つかりません")

    @Test
    fun `成功は値を持つ`() {
        expectThat(Success(1).orElse(0)).isEqualTo(1)
    }

    @Test
    fun `失敗は代わりの値を返す`() {
        val outcome: Outcome<ZettaiError, Int> = Failure(error)

        expectThat(outcome.orElse(0)).isEqualTo(0)
    }

    @Test
    fun `成功は map で変換される`() {
        expectThat(Success(1).map { it + 1 }).isEqualTo(Success(2))
    }

    @Test
    fun `失敗は map で変換されずそのまま流れる`() {
        val outcome: Outcome<ZettaiError, Int> = Failure(error)

        expectThat(outcome.map { it + 1 }).isEqualTo(Failure(error))
    }

    @Test
    fun `fold で成功と失敗を 1 つの値にできる`() {
        expectThat(Success(1).fold({ "失敗" }, { "成功 $it" })).isEqualTo("成功 1")
        val outcome: Outcome<ZettaiError, Int> = Failure(error)

        expectThat(outcome.fold({ it.message }, { "成功" })).isEqualTo("book が見つかりません")
    }

    @Test
    fun `失敗の理由が型で区別できる`() {
        val failures: List<ZettaiError> = listOf(
            ListNotFound("なし"),
            InvalidTransition("だめ")
        )

        expectThat(failures.map { it::class.simpleName })
            .isEqualTo(listOf("ListNotFound", "InvalidTransition"))
    }
}
