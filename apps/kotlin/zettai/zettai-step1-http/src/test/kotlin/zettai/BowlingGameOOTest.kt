package zettai

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

/**
 * 状態を持つ [BowlingGame] のテスト。
 *
 * 第 1 章で関数型のテストと比較するために置いている。
 * フィールドで game を持ち、rollMany が状態を書き換える点が
 * [BowlingGameTest] との違い。
 */
class BowlingGameOOTest {
    private val game = BowlingGame()

    private fun rollMany(times: Int, pins: Int) {
        repeat(times) { game.roll(pins) }
    }

    @Test
    fun `スペアは次の 1 投を加算する`() {
        game.roll(5)
        game.roll(5)
        game.roll(3)
        rollMany(17, 0)

        expectThat(game.score()).isEqualTo(16)
    }
}
