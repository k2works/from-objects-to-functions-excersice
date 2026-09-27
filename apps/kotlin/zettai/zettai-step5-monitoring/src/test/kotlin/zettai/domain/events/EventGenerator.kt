package zettai.domain.events

import kotlin.random.Random
import zettai.domain.ListName
import zettai.domain.ToDoItem
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

    private fun event(random: Random): ToDoListEvent {
        val user = users.random(random)
        val listName = listNames.random(random)

        return if (random.nextBoolean()) {
            ListCreated(user, listName)
        } else {
            ItemAdded(user, listName, ToDoItem(descriptions.random(random)))
        }
    }
}
