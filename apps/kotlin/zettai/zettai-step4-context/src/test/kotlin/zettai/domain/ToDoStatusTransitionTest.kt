package zettai.domain

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import strikt.api.expectThat
import strikt.assertions.isEqualTo

/**
 * 遷移表の 16 マスをすべて確かめる。
 *
 * 許す遷移だけを確かめると、「実は何でも通る」実装でも green になる。
 * 表を先に作り、表から全マスを起こす。
 */
class ToDoStatusTransitionTest {

    /** 遷移表。行が現在の状態、列が次の状態。 */
    private val table: Map<ToDoStatus, Map<ToDoStatus, Boolean>> = mapOf(
        ToDoStatus.Todo to mapOf(
            ToDoStatus.Todo to false,
            ToDoStatus.InProgress to true,
            ToDoStatus.Done to true,
            ToDoStatus.Blocked to false
        ),
        ToDoStatus.InProgress to mapOf(
            ToDoStatus.Todo to false,
            ToDoStatus.InProgress to false,
            ToDoStatus.Done to true,
            ToDoStatus.Blocked to true
        ),
        ToDoStatus.Done to mapOf(
            ToDoStatus.Todo to false,
            ToDoStatus.InProgress to false,
            ToDoStatus.Done to false,
            ToDoStatus.Blocked to false
        ),
        ToDoStatus.Blocked to mapOf(
            ToDoStatus.Todo to false,
            ToDoStatus.InProgress to true,
            ToDoStatus.Done to false,
            ToDoStatus.Blocked to false
        )
    )

    @TestFactory
    fun `遷移表のすべてのマス`(): List<DynamicTest> =
        table.flatMap { (from, row) ->
            row.map { (to, expected) ->
                val verb = if (expected) "許す" else "許さない"
                DynamicTest.dynamicTest("$from から $to へは$verb") {
                    expectThat(from.canTransitionTo(to)).isEqualTo(expected)
                }
            }
        }

    @TestFactory
    fun `表が 16 マスある`(): List<DynamicTest> = listOf(
        DynamicTest.dynamicTest("4 状態 x 4 状態 = 16 マス") {
            expectThat(table.values.sumOf { it.size }).isEqualTo(16)
        }
    )
}
