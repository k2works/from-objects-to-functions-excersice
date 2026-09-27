package zettai.property

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains

/**
 * 繰り返しのヘルパー自身を検査する。
 *
 * 「境界を踏んでいる」と書いても、踏んでいなければ意味がない。
 * Unit 5 で検査そのものに漏れがあった（検査対象が 0 件でも通った）ので、
 * 検査の側もテストで固定する。
 */
class PropertyTestSelfTest {

    @Test
    fun `範囲の最小と最大と 0 を試す`() {
        val drawn = mutableListOf<Int>()

        forAllRandom(trials = 1) { random -> drawn += random.nextInt(-10, 11) }

        expectThat(drawn).contains(-10)
        expectThat(drawn).contains(10)
        expectThat(drawn).contains(0)
    }

    @Test
    fun `0 が範囲の外なら端に寄せる`() {
        val drawn = mutableListOf<Int>()

        forAllRandom(trials = 1) { random -> drawn += random.nextInt(5, 9) }

        expectThat(drawn).contains(5)
        expectThat(drawn).contains(8)
    }

    @Test
    fun `一覧から選ぶときも先頭と末尾を試す`() {
        val drawn = mutableListOf<String>()
        val values = listOf("a", "b", "c")

        forAllRandom(trials = 1) { random -> drawn += values.random(random) }

        expectThat(drawn).contains("a")
        expectThat(drawn).contains("c")
    }
}
