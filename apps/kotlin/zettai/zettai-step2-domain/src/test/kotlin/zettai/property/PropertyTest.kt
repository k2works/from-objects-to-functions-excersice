package zettai.property

import kotlin.random.Random

/**
 * 法則を確かめるための繰り返し。
 *
 * 例をいくつか挙げても法則は示せないので、ランダムな入力を多数試す。
 * シードを固定しているので、失敗したら同じ入力を再現できる。
 *
 * ライブラリ（kotest-property）は入れていない。生成器はドメインごとに
 * 自前で書くことになり、書く量があまり減らないため（ADR-005）。
 * 重複していたのはこの繰り返しの骨格だけなので、ここに切り出した。
 */
const val DEFAULT_TRIALS = 200

/** ランダムな入力で check を trials 回試す。 */
fun forAllRandom(trials: Int = DEFAULT_TRIALS, check: (Random) -> Unit) {
    repeat(trials) { seed -> check(Random(seed)) }
}
