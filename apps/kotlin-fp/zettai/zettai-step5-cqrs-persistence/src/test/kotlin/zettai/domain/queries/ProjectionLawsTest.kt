package zettai.domain.queries

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import zettai.arbError
import zettai.arbEvents
import zettai.domain.ToDoStatus
import zettai.domain.errors.ZettaiError
import zettai.domain.events.replayFrom
import zettai.genListNames
import zettai.genUsers
import zettai.interleave

class ProjectionLawsTest : StringSpec({
    "射影の行は、replay した状態から数えた行と一致する" {
        checkAll(arbEvents) { events ->
            val state = events.replayFrom()

            genUsers.forEach { user ->
                val fromState = genListNames
                    .mapNotNull { name ->
                        state.listFor(user, name)?.let { list ->
                            ToDoListRow(
                                listName = name,
                                itemCount = list.items.size,
                                doneCount = list.items.count { it.status == ToDoStatus.Done },
                            )
                        }
                    }
                    .associateBy { it.listName }

                projectFrom(user, events).associateBy { it.listName } shouldBe fromState
            }
        }
    }

    "他のユーザーのイベントを足しても行は変わらない" {
        checkAll(arbEvents, arbEvents) { own, others ->
            val user = genUsers.first()
            val ownEvents = own.filter { it.user == user }
            val foreign = others.filter { it.user != user }

            projectFrom(user, interleave(ownEvents, foreign)) shouldBe projectFrom(user, ownEvents)
        }
    }

    // クエリ結果は Either の中に List が入った二重の文脈になる。
    val results: Arb<Either<ZettaiError, List<ToDoListRow>>> = Arb.choice(
        arbEvents.map { projectFrom(genUsers.first(), it).right() },
        arbError.map { it.left() },
    )
    val rowToInt: Arb<(ToDoListRow) -> Int> =
        Arb.int(-5..5).map { k -> { row: ToDoListRow -> row.itemCount * k + row.doneCount } }
    val intToText: Arb<(Int) -> String> =
        Arb.string(0..3).map { prefix -> { n: Int -> "$prefix$n" } }

    "クエリ結果の map は恒等則を満たす" {
        checkAll(results) { result ->
            result.map { it } shouldBe result
        }
    }

    "クエリ結果の内側の map も恒等則を満たす" {
        checkAll(results) { result ->
            result.map { rows -> rows.map { it } } shouldBe result
        }
    }

    "クエリ結果の map は合成則を満たす" {
        checkAll(results, rowToInt, intToText) { result, f, g ->
            result.map { rows -> rows.map(f) }.map { values -> values.map(g) } shouldBe
                result.map { rows -> rows.map { g(f(it)) } }
        }
    }
})
