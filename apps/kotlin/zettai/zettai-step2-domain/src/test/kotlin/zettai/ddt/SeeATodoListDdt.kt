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

    @TestFactory
    fun `項目を追加できる`() = ddtScenario {
        val uberto = ToDoListOwner("uberto")

        play(
            uberto.`creates a list`("book"),
            uberto.`adds an item`("book", "write chapter"),
            uberto.`adds an item`("book", "publish book"),
            uberto.`sees items`("book", listOf("write chapter", "publish book"))
        )
    }

    @TestFactory
    fun `項目に着手できる`() = ddtScenario {
        val uberto = ToDoListOwner("uberto")

        play(
            uberto.`creates a list`("book"),
            uberto.`adds an item`("book", "write chapter"),
            uberto.`starts working on`("book", "write chapter"),
            uberto.`sees the item in progress`("book", "write chapter")
        )
    }

    @TestFactory
    fun `完了した項目は再開できない`() = ddtScenario {
        val uberto = ToDoListOwner("uberto")

        play(
            uberto.`creates a list`("book"),
            uberto.`adds an item`("book", "write chapter"),
            uberto.`completes`("book", "write chapter"),
            uberto.`cannot reopen`("book", "write chapter")
        )
    }

    @TestFactory
    fun `失敗の理由が伝わる`() = ddtScenario {
        val uberto = ToDoListOwner("uberto")

        play(
            uberto.`creates a list`("book"),
            uberto.`is told the list is missing`("missing")
        )
    }

    companion object {
        val allActions = listOf(DomainOnlyActions(), HttpActions())
    }
}
