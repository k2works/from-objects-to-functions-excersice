package zettai.fp

import kotlin.random.Random
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

/**
 * Outcome の map がファンクタであることを確かめる。
 *
 * ファンクタの条件は 2 つだけ。
 *   - 恒等則: map { it } は何もしないのと同じ
 *   - 合成則: map(f).map(g) は map(f して g) と同じ
 *
 * 第 5 章のモノイド則と同じ形で書ける。ランダムな入力を 200 回試す。
 */
class OutcomeFunctorTest {

    private val trials = 200

    private val f: (Int) -> Int = { it * 2 }
    private val g: (Int) -> String = { "値は $it" }

    @Test
    fun `恒等則が成り立つ`() {
        repeatWithRandomOutcomes { outcome ->
            expectThat(outcome.map { it }).isEqualTo(outcome)
        }
    }

    @Test
    fun `合成則が成り立つ`() {
        repeatWithRandomOutcomes { outcome ->
            expectThat(outcome.map(f).map(g)).isEqualTo(outcome.map { g(f(it)) })
        }
    }

    @Test
    fun `失敗は map を通り抜ける`() {
        repeatWithRandomOutcomes { outcome ->
            if (outcome is Failure) {
                expectThat(outcome.map(f)).isEqualTo(outcome)
            }
        }
    }

    private fun repeatWithRandomOutcomes(check: (Outcome<ZettaiError, Int>) -> Unit) {
        repeat(trials) { seed ->
            check(OutcomeGenerator.outcome(Random(seed)))
        }
    }
}

/** 成功と失敗をランダムに作る。失敗の理由も混ぜる。 */
object OutcomeGenerator {

    private val errors: List<ZettaiError> = listOf(
        ListNotFound("なし"),
        ListAlreadyExists("ある"),
        ItemNotFound("項目なし"),
        InvalidTransition("だめ")
    )

    fun outcome(random: Random): Outcome<ZettaiError, Int> =
        if (random.nextBoolean()) {
            Success(random.nextInt(-100, 100))
        } else {
            Failure(errors.random(random))
        }
}
