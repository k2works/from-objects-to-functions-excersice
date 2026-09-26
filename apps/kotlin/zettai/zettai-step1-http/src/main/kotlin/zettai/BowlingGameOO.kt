package zettai

/**
 * ボウリングの得点計算を、状態を持つオブジェクトとして書いた版。
 *
 * 第 1 章で関数型の書き方と比較するために置いている。
 * 振る舞いは [scoreOf] と同じで、違いは状態をどこに置くかだけ。
 */
class BowlingGame {
    private val rolls = mutableListOf<Int>()

    fun roll(pins: Int) {
        rolls.add(pins)
    }

    fun score(): Int = scoreOf(rolls)
}
