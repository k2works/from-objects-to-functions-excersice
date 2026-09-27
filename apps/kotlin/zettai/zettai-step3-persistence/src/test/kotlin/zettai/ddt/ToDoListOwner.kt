package zettai.ddt

import com.ubertob.pesticide.core.DdtActor
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNull
import strikt.assertions.isTrue
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoList
import zettai.domain.ToDoStatus
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

    fun `creates a list`(listName: String) =
        step(listName) {
            createList(user, ListName(listName))
        }

    fun `sees an empty list`(listName: String) =
        step(listName) {
            expectThat(getToDoList(user, ListName(listName))).isEqualTo(emptyList())
        }

    fun `adds an item`(listName: String, description: String) =
        step(listName, description) {
            addItem(user, ListName(listName), ToDoItem(description))
        }

    fun `sees items`(listName: String, descriptions: List<String>) =
        step(listName, descriptions) {
            expectThat(getToDoList(user, ListName(listName))?.map { it.description })
                .isEqualTo(descriptions)
        }

    fun `starts working on`(listName: String, description: String) =
        step(listName, description) {
            expectThat(changeItemStatus(user, ListName(listName), description, ToDoStatus.InProgress)).isTrue()
        }

    fun `sees the item in progress`(listName: String, description: String) =
        step(listName, description) {
            expectThat(getToDoList(user, ListName(listName))?.first { it.description == description }?.status)
                .isEqualTo(ToDoStatus.InProgress)
        }

    fun `cannot reopen`(listName: String, description: String) =
        step(listName, description) {
            expectThat(changeItemStatus(user, ListName(listName), description, ToDoStatus.InProgress)).isFalse()
        }

    fun `completes`(listName: String, description: String) =
        step(listName, description) {
            expectThat(changeItemStatus(user, ListName(listName), description, ToDoStatus.Done)).isTrue()
        }

    fun `is told the list is missing`(listName: String) =
        step(listName) {
            expectThat(errorFor(user, ListName(listName))).isEqualTo("$listName が見つかりません")
        }

    fun `cannot see the list`(listName: String) =
        step(listName) {
            expectThat(getToDoList(user, ListName(listName))).isNull()
        }
}
