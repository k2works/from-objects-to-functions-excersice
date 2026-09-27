package zettai.json

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import zettai.fp.Converter
import zettai.fp.Success
import zettai.fp.identityConverter
import zettai.property.forAllRandom

/**
 * Converter がプロファンクタであることを確かめる。
 *
 * プロファンクタの条件は 2 つ。
 *   - 恒等則: map(id, id) も contramap(id, id) も何もしないのと同じ
 *   - 合成則: map を 2 回も、まとめて 1 回も同じ（contramap も同様）
 *
 * 第 5 章（モノイド）・第 7 章（ファンクタ）・第 9 章（モナド）・
 * 第 11 章（アプリカティブ）と同じヘルパー・同じ形で書ける。5 回目。
 */
class ConverterProfunctorTest {
    /** 数を文字列にする変換。これを土台に法則を確かめる。 */
    private val intToText: Converter<Int, String> =
        Converter(render = Int::toString, parse = { Success(it.toInt()) })

    @Test
    fun `出力側の恒等則が成り立つ`() {
        forAllRandom { random ->
            val value = random.nextInt(-1000, 1000)
            val mapped = intToText.map(to = { it }, from = { it })

            expectThat(mapped.render(value)).isEqualTo(intToText.render(value))
            expectThat(mapped.parse(value.toString())).isEqualTo(intToText.parse(value.toString()))
        }
    }

    @Test
    fun `入力側の恒等則が成り立つ`() {
        forAllRandom { random ->
            val value = random.nextInt(-1000, 1000)
            val contramapped = intToText.contramap(to = { it: Int -> it }, from = { it })

            expectThat(contramapped.render(value)).isEqualTo(intToText.render(value))
            expectThat(contramapped.parse(value.toString())).isEqualTo(intToText.parse(value.toString()))
        }
    }

    @Test
    fun `出力側の合成則が成り立つ`() {
        forAllRandom { random ->
            val value = random.nextInt(-1000, 1000)

            // 2 回に分けて変換する（String → 括弧つき → さらに印つき）
            val twice = intToText
                .map(to = { "($it)" }, from = { it.removeSurrounding("(", ")") })
                .map(to = { "[$it]" }, from = { it.removeSurrounding("[", "]") })

            // まとめて 1 回で変換する
            val once = intToText.map(
                to = { "[($it)]" },
                from = { it.removeSurrounding("[", "]").removeSurrounding("(", ")") }
            )

            expectThat(twice.render(value)).isEqualTo(once.render(value))
            expectThat(twice.roundTrip(value)).isEqualTo(once.roundTrip(value))
        }
    }

    @Test
    fun `入力側の合成則が成り立つ`() {
        forAllRandom { random ->
            val value = random.nextInt(-500, 500)

            // 逆向き（from）も検証するので、往復できる変換を選ぶ。
            // it * 2 と it / 2 は整数の割り算で奇数が戻らないため、足し算にする
            val twice = intToText
                .contramap(to = { it: Int -> it + 10 }, from = { it - 10 })
                .contramap(to = { it: Int -> it + 1 }, from = { it - 1 })

            val once = intToText.contramap(to = { it: Int -> it + 11 }, from = { it - 11 })

            expectThat(twice.render(value)).isEqualTo(once.render(value))
            expectThat(twice.parse(value.toString())).isEqualTo(once.parse(value.toString()))
        }
    }

    @Test
    fun `何も変換しない Converter は往復できる`() {
        forAllRandom { random ->
            val value = random.nextInt(-1000, 1000)

            expectThat(identityConverter<Int>().roundTrip(value)).isEqualTo(Success(value))
        }
    }
}
