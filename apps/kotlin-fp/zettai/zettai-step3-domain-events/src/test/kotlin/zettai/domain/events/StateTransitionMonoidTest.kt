package zettai.domain.events

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.User

class StateTransitionMonoidTest : StringSpec({
    val identifier = Arb.string(1..12)
    val event = Arb.choice(
        Arb.bind(identifier, identifier) { user, list ->
            ListCreated(User(user), ListName(list))
        },
        Arb.bind(identifier, identifier, identifier) { user, list, item ->
            ItemAdded(User(user), ListName(list), ToDoItem(item))
        },
    )
    val events = Arb.list(event, 0..8)

    "再帰と fold は生成したイベント列でも同じ状態を導出する" {
        checkAll(events, events) { seed, values ->
            val initial = seed.replayFrom(ToDoListState.empty)

            values.replayByRecursion(initial) shouldBe values.replayFrom(initial)
        }
    }

    "状態変換の合成には結合律が成り立つ" {
        checkAll(events, events, events, events) { seed, a, b, c ->
            val initial = seed.replayFrom(ToDoListState.empty)
            val left = (a.asTransition() andThen b.asTransition()) andThen c.asTransition()
            val right = a.asTransition() andThen (b.asTransition() andThen c.asTransition())

            left(initial) shouldBe right(initial)
        }
    }

    "identityTransition は左右の単位元である" {
        checkAll(events, events) { seed, values ->
            val initial = seed.replayFrom(ToDoListState.empty)
            val transition = values.asTransition()

            (identityTransition andThen transition)(initial) shouldBe transition(initial)
            (transition andThen identityTransition)(initial) shouldBe transition(initial)
        }
    }

    "イベント列の連結と状態変換の合成は同じ結果になる" {
        checkAll(events, events, events) { seed, a, b ->
            val initial = seed.replayFrom(ToDoListState.empty)

            (a + b).asTransition()(initial) shouldBe
                (a.asTransition() andThen b.asTransition())(initial)
        }
    }
})
