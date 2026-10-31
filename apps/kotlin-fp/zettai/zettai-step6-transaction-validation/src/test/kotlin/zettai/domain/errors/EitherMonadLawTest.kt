package zettai.domain.errors

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import zettai.arbError

class EitherMonadLawTest : StringSpec({
    fun <A> pure(value: A): Either<ZettaiError, A> = value.right()

    val values: Arb<Int> = Arb.int()
    val results: Arb<Either<ZettaiError, Int>> = Arb.choice(
        values.map { it.right() },
        arbError.map { it.left() },
    )

    // 割り切れるかどうかで成否が分かれる関数。成功も失敗も、失敗の種類も生成される。
    val steps: Arb<(Int) -> Either<ZettaiError, Int>> =
        Arb.bind(Arb.int(2..5), Arb.int(-5..5), arbError) { divisor, offset, error ->
            { x: Int -> if (x % divisor == 0) error.left() else (x + offset).right() }
        }

    "左恒等則：pure(a).flatMap(f) は f(a) と同じ" {
        checkAll(values, steps) { a, f ->
            pure(a).flatMap(f) shouldBe f(a)
        }
    }

    "右恒等則：m.flatMap(pure) は m と同じ" {
        checkAll(results) { m ->
            m.flatMap { pure(it) } shouldBe m
        }
    }

    "結合律：flatMap を続けても、中にまとめても同じ" {
        checkAll(results, steps, steps) { m, f, g ->
            m.flatMap(f).flatMap(g) shouldBe m.flatMap { x -> f(x).flatMap(g) }
        }
    }

    // Hub の `either { ... bind() ... }` は、flatMap の連鎖を上から読める形で書いたもの。
    "Raise の bind の連鎖は flatMap と同じ結果になる" {
        checkAll(results, steps, steps) { m, f, g ->
            val withBind: Either<ZettaiError, Int> = either { g(f(m.bind()).bind()).bind() }

            withBind shouldBe m.flatMap(f).flatMap(g)
        }
    }

    "最初の失敗で打ち切り、後の関数は呼ばれない" {
        checkAll(arbError, steps) { error, f ->
            var called = false
            val result: Either<ZettaiError, Int> = error.left<ZettaiError>().flatMap { x: Int ->
                called = true
                f(x)
            }

            result shouldBe error.left()
            called shouldBe false
        }
    }
})
