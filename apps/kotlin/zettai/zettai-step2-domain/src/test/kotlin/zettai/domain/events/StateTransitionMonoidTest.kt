package zettai.domain.events

import kotlin.random.Random
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

/**
 * 状態変換がモノイドであることを確かめる。
 *
 * モノイドの条件は 2 つだけ。
 *   - 結合律: (f andThen g) andThen h == f andThen (g andThen h)
 *   - 単位元: identity andThen f == f == f andThen identity
 *
 * 例を 3 つ挙げても「成り立つ」とは言えないので、
 * ランダムな入力を 200 回試して反例が出ないことを確かめる。
 */
class StateTransitionMonoidTest {

    private val trials = 200

    @Test
    fun `結合律が成り立つ`() {
        repeatWithRandomEvents { a, b, c ->
            val left = (a.asTransition() andThen b.asTransition()) andThen c.asTransition()
            val right = a.asTransition() andThen (b.asTransition() andThen c.asTransition())

            expectThat(left(ToDoListState.empty)).isEqualTo(right(ToDoListState.empty))
        }
    }

    @Test
    fun `単位元が成り立つ`() {
        repeatWithRandomEvents { a, _, _ ->
            val f = a.asTransition()

            expectThat((identityTransition andThen f)(ToDoListState.empty))
                .isEqualTo(f(ToDoListState.empty))
            expectThat((f andThen identityTransition)(ToDoListState.empty))
                .isEqualTo(f(ToDoListState.empty))
        }
    }

    @Test
    fun `列の連結と変換の合成は同じ結果になる`() {
        repeatWithRandomEvents { a, b, _ ->
            val concatenated = (a + b).asTransition()
            val composed = a.asTransition() andThen b.asTransition()

            expectThat(concatenated(ToDoListState.empty)).isEqualTo(composed(ToDoListState.empty))
        }
    }

    private fun repeatWithRandomEvents(
        check: (List<ToDoListEvent>, List<ToDoListEvent>, List<ToDoListEvent>) -> Unit
    ) {
        repeat(trials) { seed ->
            val random = Random(seed)

            check(
                EventGenerator.events(random, random.nextInt(0, 5)),
                EventGenerator.events(random, random.nextInt(0, 5)),
                EventGenerator.events(random, random.nextInt(0, 5))
            )
        }
    }
}
