package zettai.ddt

import com.ubertob.pesticide.core.DomainDrivenTest
import org.junit.jupiter.api.TestFactory

/**
 * 同じシナリオを、すべての経路で実行する。
 *
 * シナリオの記述に HTTP もドメインの実装も出てこない。
 * 経路を差し替えても、確かめている内容は変わらない。
 */
class SeeATodoListDdt : DomainDrivenTest<ZettaiActions>(allActions) {

    @TestFactory
    fun `ToDo リストを見る`() = ddtScenario {
        val uberto = ToDoListOwner("uberto")

        play(
            uberto.`has a list`("book", listOf("write chapter", "publish book")),
            uberto.`can see the list`("book", listOf("write chapter", "publish book"))
        )
    }

    @TestFactory
    fun `存在しないリストは見られない`() = ddtScenario {
        val uberto = ToDoListOwner("uberto")

        play(
            uberto.`has a list`("book", emptyList()),
            uberto.`cannot see the list`("missing")
        )
    }

    @TestFactory
    fun `作ったリストが表示される`() = ddtScenario {
        val uberto = ToDoListOwner("uberto")

        play(
            uberto.`creates a list`("shopping"),
            uberto.`sees an empty list`("shopping")
        )
    }

    companion object {
        val allActions = listOf(DomainOnlyActions(), HttpActions())
    }
}
