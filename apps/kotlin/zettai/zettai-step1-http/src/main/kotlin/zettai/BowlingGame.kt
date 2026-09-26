package zettai

/**
 * ボウリング 1 ゲームの得点を返す。
 *
 * 投球の並びを受け取って点数を返すだけの純粋関数として書く。
 * ゲームの途中経過を保持するオブジェクトを作らないので、
 * テストは「入力を与えて出力を確かめる」形だけで済む。
 */
fun scoreOf(rolls: List<Int>): Int = scoreFrames(rolls, remainingFrames = FRAMES_PER_GAME)

private const val FRAMES_PER_GAME = 10
private const val ALL_PINS = 10

private fun scoreFrames(rolls: List<Int>, remainingFrames: Int): Int =
    when {
        remainingFrames == 0 -> 0
        rolls.isStrike() -> rolls.frameScore(rollsUsed = 1, bonusRolls = 2) + rolls.nextFrames(1, remainingFrames)
        rolls.isSpare() -> rolls.frameScore(rollsUsed = 2, bonusRolls = 1) + rolls.nextFrames(2, remainingFrames)
        else -> rolls.frameScore(rollsUsed = 2, bonusRolls = 0) + rolls.nextFrames(2, remainingFrames)
    }

private fun List<Int>.isStrike(): Boolean = first() == ALL_PINS

private fun List<Int>.isSpare(): Boolean = take(2).sum() == ALL_PINS

/** そのフレームで使う投球数と、ボーナスとして加算する投球数の合計を足す。 */
private fun List<Int>.frameScore(rollsUsed: Int, bonusRolls: Int): Int = take(rollsUsed + bonusRolls).sum()

private fun List<Int>.nextFrames(rollsUsed: Int, remainingFrames: Int): Int =
    scoreFrames(drop(rollsUsed), remainingFrames - 1)
