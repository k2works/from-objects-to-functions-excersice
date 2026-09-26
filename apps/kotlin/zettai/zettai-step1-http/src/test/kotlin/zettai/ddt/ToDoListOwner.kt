package zettai.ddt

import com.ubertob.pesticide.core.DdtActor
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoList
import zettai.domain.User

/**
 * ToDo リストの持ち主。
 *
 * シナリオはこのアクターの言葉で書く。アクターが知っているのは業務の操作だけで、
 * それがどう実行されるかは ZettaiActions の実装が決める。
 */
data class ToDoListOwner(override val name: String) : DdtActor<ZettaiActions>() {

    val user = User(name)

    fun `has a list`(listName: String, items: List<String>) =
        step(listName, items) {
            setUp(user, ToDoList(ListName(listName), items.map(::ToDoItem)))
        }

    fun `can see the list`(listName: String, items: List<String>) =
        step(listName, items) {
            expectThat(getToDoList(user, ListName(listName)))
                .isEqualTo(items.map(::ToDoItem))
        }

    fun `cannot see the list`(listName: String) =
        step(listName) {
            expectThat(getToDoList(user, ListName(listName))).isNull()
        }
}
