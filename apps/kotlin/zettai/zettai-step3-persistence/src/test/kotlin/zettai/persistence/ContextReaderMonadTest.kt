package zettai.persistence

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import zettai.fp.ContextReader
import zettai.fp.pure
import zettai.property.forAllRandom

/**
 * ContextReader がモナドであることを確かめる。
 *
 * モナドの条件は 3 つ。
 *   - 左恒等則: pure(x).flatMap(f) == f(x)
 *   - 右恒等則: m.flatMap(::pure) == m
 *   - 結合律: m.flatMap(f).flatMap(g) == m.flatMap { f(it).flatMap(g) }
 *
 * 第 5 章のモノイド則、第 7 章のファンクタ則と同じ形で書ける。
 * 文脈（ここでは Int）を渡して実行し、結果を比べる。
 */
class ContextReaderMonadTest {
    private val f: (Int) -> ContextReader<Int, Int> = { x -> ContextReader { context -> x + context } }
    private val g: (Int) -> ContextReader<Int, String> = { x -> ContextReader { context -> "$x/$context" } }

    @Test
    fun `左恒等則が成り立つ`() {
        forAllRandom { random ->
            val x = random.nextInt(-100, 100)
            val context = random.nextInt(-100, 100)

            expectThat(pure<Int, Int>(x).flatMap(f).runWith(context)).isEqualTo(f(x).runWith(context))
        }
    }

    @Test
    fun `右恒等則が成り立つ`() {
        forAllRandom { random ->
            val context = random.nextInt(-100, 100)
            val m = ContextReader<Int, Int> { it * 2 }

            expectThat(m.flatMap { pure<Int, Int>(it) }.runWith(context)).isEqualTo(m.runWith(context))
        }
    }

    @Test
    fun `結合律が成り立つ`() {
        forAllRandom { random ->
            val context = random.nextInt(-100, 100)
            val m = ContextReader<Int, Int> { it * 2 }

            expectThat(m.flatMap(f).flatMap(g).runWith(context))
                .isEqualTo(m.flatMap { f(it).flatMap(g) }.runWith(context))
        }
    }

    @Test
    fun `map は flatMap と pure で書ける`() {
        forAllRandom { random ->
            val context = random.nextInt(-100, 100)
            val m = ContextReader<Int, Int> { it * 2 }
            val h: (Int) -> String = { "値は $it" }

            expectThat(m.map(h).runWith(context)).isEqualTo(m.flatMap { pure<Int, String>(h(it)) }.runWith(context))
        }
    }
}
