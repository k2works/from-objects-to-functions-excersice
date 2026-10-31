package zettai.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ToDoStatusTransitionTest : StringSpec({
    val table = mapOf(
        ToDoStatus.Todo to mapOf(
            ToDoStatus.Todo to false,
            ToDoStatus.InProgress to true,
            ToDoStatus.Done to true,
            ToDoStatus.Blocked to false,
        ),
        ToDoStatus.InProgress to mapOf(
            ToDoStatus.Todo to false,
            ToDoStatus.InProgress to false,
            ToDoStatus.Done to true,
            ToDoStatus.Blocked to true,
        ),
        ToDoStatus.Done to ToDoStatus.entries.associateWith { false },
        ToDoStatus.Blocked to mapOf(
            ToDoStatus.Todo to false,
            ToDoStatus.InProgress to true,
            ToDoStatus.Done to false,
            ToDoStatus.Blocked to false,
        ),
    )

    "4×4 の状態遷移表をすべて満たす" {
        table.size shouldBe 4
        table.values.sumOf { it.size } shouldBe 16
        table.values.sumOf { row -> row.count { it.value } } shouldBe 5

        table.forEach { (from, row) ->
            row.forEach { (to, expected) ->
                from.canTransitionTo(to) shouldBe expected
            }
        }
    }
})
