package zettai.domain.validation

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.right
import arrow.core.toNonEmptyListOrNull
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/** 蓄積する合成 [zipAll] が満たす性質。誤りは `String` で表し、順序と個数を観察しやすくする。 */
class AccumulationLawsTest : StringSpec({
    val problems: Arb<NonEmptyList<String>> =
        Arb.list(Arb.string(1..3), 1..4).map { requireNotNull(it.toNonEmptyListOrNull()) }
    val results: Arb<Either<NonEmptyList<String>, Int>> = Arb.choice(
        Arb.int(-100..100).map { it.right() },
        problems.map { it.left() },
    )
    val unit: Either<NonEmptyList<String>, Unit> = Unit.right()

    "蓄積する結果の map は恒等則を満たす" {
        checkAll(results) { result ->
            result.map { it } shouldBe result
        }
    }

    "蓄積する結果の map は合成則を満たす" {
        checkAll(results, Arb.int(-5..5), Arb.string(0..3)) { result, k, prefix ->
            val f = { x: Int -> x * k }
            val g = { x: Int -> "$prefix$x" }

            result.map(f).map(g) shouldBe result.map { g(f(it)) }
        }
    }

    "どちらも失敗なら、誤りを左から順に、すべて集める" {
        checkAll(problems, problems) { first, second ->
            val result = zipAll(first.left(), second.left()) { a: Int, b: Int -> a + b }

            result shouldBe (first + second).left()
        }
    }

    "片方だけが失敗なら、その誤りだけを返す" {
        checkAll(problems, Arb.int()) { failed, value ->
            zipAll(failed.left(), value.right()) { _: Int, _: Int -> 0 } shouldBe failed.left()
            zipAll(value.right(), failed.left()) { _: Int, _: Int -> 0 } shouldBe failed.left()
        }
    }

    "誤りの個数は、失敗した側の個数の合計になる" {
        checkAll(results, results) { first, second ->
            val result = zipAll(first, second) { a, b -> a + b }

            result.leftOrNull()?.size.orZero() shouldBe
                first.leftOrNull()?.size.orZero() + second.leftOrNull()?.size.orZero()
        }
    }

    "結合律：どこから合成しても、成功の値も、誤りの並びも同じ" {
        checkAll(results, results, results) { a, b, c ->
            val leftFirst = zipAll(zipAll(a, b) { x, y -> x to y }, c) { xy, z -> Triple(xy.first, xy.second, z) }
            val rightFirst = zipAll(a, zipAll(b, c) { y, z -> y to z }) { x, yz -> Triple(x, yz.first, yz.second) }

            leftFirst shouldBe rightFirst
        }
    }

    "単位元：成功する Unit と合成しても、結果は変わらない" {
        checkAll(results) { a ->
            zipAll(a, unit) { x, _ -> x } shouldBe a
            zipAll(unit, a) { _, x -> x } shouldBe a
        }
    }

    "すべて成功なら、ただの関数の適用と同じ" {
        checkAll(Arb.int(), Arb.int()) { a, b ->
            zipAll<String, Int, Int, Int>(a.right(), b.right()) { x, y -> x * 2 + y } shouldBe (a * 2 + b).right()
        }
    }

    "3 つの合成も、誤りを左から順にすべて集める" {
        checkAll(problems, problems, problems) { a, b, c ->
            zipAll<String, Int, Int, Int, Int>(a.left(), b.left(), c.left()) { x, y, z -> x + y + z } shouldBe
                (a + b + c).left()
        }
    }
})

private fun Int?.orZero(): Int = this ?: 0
