package zettai.fp

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import zettai.property.forAllRandom

/**
 * Validation がアプリカティブであることを確かめる。
 *
 * アプリカティブの条件はファンクタの 2 つに加えて、
 *   - 合わせる操作（combine）が結合的であること
 *   - 失敗が失われないこと（全部集まる）
 *
 * 第 5 章のモノイド則、第 7 章のファンクタ則、第 9 章のモナド則と同じ形で書ける。
 */
class ValidationApplicativeTest {
    private val f: (Int) -> Int = { it * 2 }
    private val g: (Int) -> String = { "値は $it" }

    @Test
    fun `恒等則が成り立つ`() {
        forAllRandom { random ->
            val v = ValidationGenerator.validation(random)

            expectThat(v.map { it }).isEqualTo(v)
        }
    }

    @Test
    fun `合成則が成り立つ`() {
        forAllRandom { random ->
            val v = ValidationGenerator.validation(random)

            expectThat(v.map(f).map(g)).isEqualTo(v.map { g(f(it)) })
        }
    }

    @Test
    fun `combine は結合的`() {
        forAllRandom { random ->
            val a = ValidationGenerator.validation(random)
            val b = ValidationGenerator.validation(random)
            val c = ValidationGenerator.validation(random)

            val left = combine(combine(a, b) { x, y -> "$x$y" }, c) { xy, z -> "$xy$z" }
            val right = combine(a, combine(b, c) { y, z -> "$y$z" }) { x, yz -> "$x$yz" }

            expectThat(left).isEqualTo(right)
        }
    }

    @Test
    fun `失敗はひとつも失われない`() {
        forAllRandom { random ->
            val a = ValidationGenerator.validation(random)
            val b = ValidationGenerator.validation(random)

            val combined = combine(a, b) { x, y -> "$x$y" }
            val expected = a.errorCount() + b.errorCount()

            expectThat(combined.errorCount()).isEqualTo(expected)
        }
    }

    private fun Validation<*>.errorCount(): Int =
        when (this) {
            is Valid -> 0
            is Invalid -> errors.size
        }
}

/** 成功と失敗をランダムに作る。失敗の数も混ぜる。 */
object ValidationGenerator {
    fun validation(random: kotlin.random.Random): Validation<Int> =
        if (random.nextBoolean()) {
            Valid(random.nextInt(-100, 100))
        } else {
            Invalid(List(random.nextInt(1, 4)) { "エラー $it" })
        }
}
