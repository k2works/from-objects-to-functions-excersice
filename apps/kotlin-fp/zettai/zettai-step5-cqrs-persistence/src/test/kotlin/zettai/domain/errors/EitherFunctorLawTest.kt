package zettai.domain.errors

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import zettai.arbError

class EitherFunctorLawTest : StringSpec({
    val names = Arb.string(1..12)
    val outcomes: Arb<Either<ZettaiError, Int>> = Arb.choice(
        Arb.int().map { it.right() },
        arbError.map { it.left() },
    )
    val intFunctions: Arb<(Int) -> Int> =
        Arb.bind(Arb.int(), Arb.int()) { k, c -> { x: Int -> x * k + c } }
    val showFunctions: Arb<(Int) -> String> =
        names.map { prefix -> { x: Int -> "$prefix$x" } }

    fun Either<ZettaiError, Int>.label(): String =
        fold({ it::class.simpleName.orEmpty() }, { "Right" })

    "Either map の恒等則が成り立つ" {
        checkAll(outcomes) { outcome ->
            collect(outcome.label())
            outcome.map { it } shouldBe outcome
        }
    }

    "Either map の合成則が成り立つ" {
        checkAll(outcomes, intFunctions, showFunctions) { outcome, f, g ->
            collect(outcome.label())
            outcome.map(f).map(g) shouldBe outcome.map { g(f(it)) }
        }
    }
})
