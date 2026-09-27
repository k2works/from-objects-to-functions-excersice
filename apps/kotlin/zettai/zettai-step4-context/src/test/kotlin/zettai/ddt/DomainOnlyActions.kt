package zettai.ddt

import com.ubertob.pesticide.core.DdtProtocol
import com.ubertob.pesticide.core.DomainOnly
import com.ubertob.pesticide.core.DomainSetUp
import com.ubertob.pesticide.core.Ready
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoList
import zettai.domain.ToDoListHub
import zettai.domain.ToDoStatus
import zettai.domain.User
import zettai.domain.commands.AddToDoItem
import zettai.domain.commands.ChangeItemStatus
import zettai.domain.commands.CreateToDoList
import zettai.domain.commands.RenameToDoList
import zettai.domain.events.ItemAdded
import zettai.domain.events.ListCreated
import zettai.domain.events.ToDoListEvent
import zettai.domain.events.ToDoListState
import zettai.domain.events.replayFrom
import zettai.domain.queries.ToDoListProjection
import zettai.domain.queries.projectFrom
import zettai.domain.validateListName
import zettai.fp.Invalid
import zettai.fp.Success
import zettai.fp.Valid
import zettai.fp.pure

/** ドメインを直接呼ぶ経路。HTTP を経由しないので速い。 */
class DomainOnlyActions : ZettaiActions {
    override val protocol: DdtProtocol = DomainOnly

    private val events = mutableListOf<ToDoListEvent>()

    /** シナリオごとに状態を初期化する。テスト間で状態が漏れると、実行順序で結果が変わる。 */
    override fun prepare(): DomainSetUp {
        events.clear()
        return Ready
    }

    override fun tearDown(): ZettaiActions = this

    override fun setUp(user: User, list: ToDoList) {
        events += ListCreated(user, list.listName)
        list.items.forEach { events += ItemAdded(user, list.listName, it) }
    }

    override fun createList(user: User, listName: ListName) {
        runWithoutContext(hub().handle(CreateToDoList(user, listName)))
    }

    override fun addItem(user: User, listName: ListName, item: ToDoItem) {
        runWithoutContext(hub().handle(AddToDoItem(user, listName, item)))
    }

    override fun changeItemStatus(
        user: User,
        listName: ListName,
        description: String,
        status: ToDoStatus
    ): Boolean = runWithoutContext(hub().handle(ChangeItemStatus(user, listName, description, status))) is Success

    override fun errorFor(user: User, listName: ListName): String? =
        runWithoutContext(hub().itemsFor(user, listName)).fold({ it.message }, { null })

    /** ハブを組み立てる。コマンド側とクエリ側で別の経路を持つ。 */
    private fun hub() = ToDoListHub(
        fetchState = { pure(events.replayFrom(ToDoListState.empty)) },
        fetchProjection = { pure(events.projectFrom(ToDoListProjection.empty)) },
        persist = { newEvents -> pure(events.addAll(newEvents).let { }) }
    )

    override fun getToDoList(user: User, listName: ListName): List<ToDoItem>? =
        runWithoutContext(hub().itemsFor(user, listName)).fold({ null }, { it })
    override fun renameList(user: User, listName: ListName, newName: String): List<String> =
        when (val validated = validateListName(newName)) {
            is Invalid -> validated.errors
            is Valid -> if (renameTo(user, listName, validated.value)) emptyList() else listOf("名前を変更できません")
        }

    private fun renameTo(user: User, listName: ListName, newName: ListName): Boolean =
        runWithoutContext(hub().handle(RenameToDoList(user, listName, newName))) is Success

}
