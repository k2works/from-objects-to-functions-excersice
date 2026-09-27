package zettai.domain.events

import java.time.LocalDate
import kotlin.random.Random
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoStatus
import zettai.domain.User

/**
 * 出来事のランダム生成。
 *
 * 法則が成り立つことは、例をいくつか挙げても示せない。
 * ランダムな入力を多数試して、反例が出ないことを確かめる。
 */
object EventGenerator {

    private val users = listOf("uberto", "alice", "bob").map(::User)
    private val listNames = listOf("book", "shopping", "work").map(::ListName)
    private val descriptions = listOf("write chapter", "buy milk", "review PR", "publish book")

    fun events(random: Random, size: Int): List<ToDoListEvent> = List(size) { event(random) }

    /**
     * 4 種類の出来事を生成する。
     *
     * 2 種類しか生成していなかったとき、往復のテストは green でも
     * 期日と状態を含む出来事を一度も通していなかった。
     * 生成する種類が実装の種類に追いついていないと、法則の検査に穴が空く。
     */
    private fun event(random: Random): ToDoListEvent {
        val user = users.random(random)
        val listName = listNames.random(random)

        return when (random.nextInt(4)) {
            0 -> ListCreated(user, listName)

            1 -> ItemAdded(
                user,
                listName,
                ToDoItem(
                    description = descriptions.random(random),
                    dueDate = if (random.nextBoolean()) LocalDate.of(2026, 1 + random.nextInt(12), 1) else null,
                    status = ToDoStatus.entries.toList().random(random)
                )
            )

            2 -> ListRenamed(user, listName, listNames.random(random))

            else -> ItemStatusChanged(
                user,
                listName,
                descriptions.random(random),
                ToDoStatus.entries.toList().random(random)
            )
        }
    }
}
