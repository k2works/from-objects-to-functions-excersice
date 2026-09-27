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

/**
 * ランダムな入力で check を試す。
 *
 * ランダムだけでは境界（範囲の最小・最大・0）を踏まないことがある。
 * 反例は端に潜むので、先に端を試してからランダムに移る。
 */
fun forAllRandom(trials: Int = DEFAULT_TRIALS, check: (Random) -> Unit) {
    edges.forEach { edge -> check(EdgeRandom(edge)) }

    repeat(trials) { seed -> check(Random(seed)) }
}

/** 範囲の端を選ぶ方法。最小・最大・0（範囲に入るなら）。 */
private val edges: List<(Int, Int) -> Int> = listOf(
    { from, _ -> from },
    { _, until -> until - 1 },
    { from, until -> 0.coerceIn(from, until - 1) }
)

/**
 * 範囲の端を返す Random。
 *
 * nextInt(from, until) だけを差し替える。collection.random(random) も
 * この経路を通るので、先頭と末尾の要素が選ばれる。
 * ほかの取り出し方（真偽値など）は通常の Random に任せる。
 */
private class EdgeRandom(private val edge: (Int, Int) -> Int) : Random() {
    private val base = Random(0)

    override fun nextBits(bitCount: Int): Int = base.nextBits(bitCount)

    override fun nextInt(from: Int, until: Int): Int = edge(from, until)

    override fun nextInt(until: Int): Int = nextInt(0, until)
}
