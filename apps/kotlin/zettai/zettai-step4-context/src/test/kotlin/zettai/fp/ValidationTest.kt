package zettai.fp

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class ValidationTest {
    private fun nonBlank(name: String, value: String): Validation<String> =
        if (value.isNotBlank()) value.asValid() else "$name を入力してください".asInvalid()

    private fun maxLength(name: String, value: String, max: Int): Validation<String> =
        if (value.length <= max) value.asValid() else "$name は $max 文字以内にしてください".asInvalid()

    @Test
    fun `両方成功なら合わせられる`() {
        val result = combine(nonBlank("名前", "book"), nonBlank("説明", "読む本")) { a, b -> "$a/$b" }

        expectThat(result).isEqualTo(Valid("book/読む本"))
    }

    @Test
    fun `片方が失敗ならその理由が返る`() {
        val result = combine(nonBlank("名前", ""), nonBlank("説明", "読む本")) { a, b -> "$a/$b" }

        expectThat(result).isEqualTo(Invalid(listOf("名前 を入力してください")))
    }

    @Test
    fun `両方が失敗なら理由が 2 つ返る`() {
        val result = combine(nonBlank("名前", ""), nonBlank("説明", "")) { a, b -> "$a/$b" }

        expectThat(result).isEqualTo(Invalid(listOf("名前 を入力してください", "説明 を入力してください")))
    }

    @Test
    fun `3 つの検証でも失敗を全部集める`() {
        val result = combine(
            nonBlank("名前", ""),
            maxLength("説明", "x".repeat(30), 10),
            nonBlank("作者", "")
        ) { a, b, c -> "$a/$b/$c" }

        expectThat(result).isEqualTo(
            Invalid(
                listOf(
                    "名前 を入力してください",
                    "説明 は 10 文字以内にしてください",
                    "作者 を入力してください"
                )
            )
        )
    }

    @Test
    fun `Outcome は最初の失敗で止まる`() {
        var secondCalled = false

        val outcome: Outcome<ZettaiError, String> = Failure(InvalidTransition("1 つめが失敗"))
            .transform<ZettaiError, String> {
                secondCalled = true
                Success("2 つめ")
            } as Outcome<ZettaiError, String>

        // 同じ「2 つの検証」を Outcome で書くと、2 つめは実行されない
        expectThat(secondCalled).isEqualTo(false)
        expectThat(outcome is Failure).isEqualTo(true)
    }
}
