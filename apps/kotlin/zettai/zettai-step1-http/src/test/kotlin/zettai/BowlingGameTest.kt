package zettai

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class BowlingGameTest {

    @Test
    fun `すべて外したら 0 点`() {
        expectThat(scoreOf(rolls(0, times = 20))).isEqualTo(0)
    }

    @Test
    fun `毎回 1 本なら 20 点`() {
        expectThat(scoreOf(rolls(1, times = 20))).isEqualTo(20)
    }

    @Test
    fun `スペアは次の 1 投を加算する`() {
        expectThat(scoreOf(listOf(5, 5, 3) + rolls(0, times = 17))).isEqualTo(16)
    }

    @Test
    fun `ストライクは次の 2 投を加算する`() {
        expectThat(scoreOf(listOf(10, 3, 4) + rolls(0, times = 16))).isEqualTo(24)
    }

    @Test
    fun `パーフェクトゲームは 300 点`() {
        expectThat(scoreOf(rolls(10, times = 12))).isEqualTo(300)
    }

    private fun rolls(pins: Int, times: Int): List<Int> = List(times) { pins }
}
