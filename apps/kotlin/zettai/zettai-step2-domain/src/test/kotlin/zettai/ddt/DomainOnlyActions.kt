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
import zettai.domain.commands.AddToDoItem
import zettai.domain.commands.ChangeItemStatus
import zettai.domain.commands.CreateToDoList
import zettai.domain.events.ItemAdded
import zettai.domain.events.ListCreated
import zettai.domain.events.ToDoListEvent
import zettai.domain.events.ToDoListState
import zettai.domain.events.replayFrom
import zettai.fp.ListNotFound
import zettai.fp.asFailure
import zettai.fp.asSuccess
import zettai.domain.User
import zettai.web.inMemoryFetcher

/** ドメインを直接呼ぶ経路。HTTP を経由しないので速い。 */
class DomainOnlyActions : ZettaiActions {
    override val protocol: DdtProtocol = DomainOnly

    private val events = mutableListOf<ToDoListEvent>()

    private val state: ToDoListState get() = events.replayFrom(ToDoListState.empty)

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
        hub().handle(CreateToDoList(user, listName))
    }

    override fun addItem(user: User, listName: ListName, item: ToDoItem) {
        hub().handle(AddToDoItem(user, listName, item))
    }

    override fun changeItemStatus(
        user: User,
        listName: ListName,
        description: String,
        status: ToDoStatus
    ): Boolean = hub().handle(ChangeItemStatus(user, listName, description, status)) is zettai.fp.Success

    override fun errorFor(user: User, listName: ListName): String? =
        hub().getList(user, listName).fold({ it.message }, { null })

    /** ハブを組み立てる。出来事の保存先はこのオブジェクトが持つリスト。 */
    private fun hub() = ToDoListHub(
        fetchList = { u, l ->
            state.listFor(u, l)?.asSuccess() ?: ListNotFound("${l.name} が見つかりません").asFailure()
        },
        fetchState = { state },
        persist = { events += it }
    )

    override fun getToDoList(user: User, listName: ListName): List<ToDoItem>? =
        hub().getList(user, listName).fold({ null }, { it.items })
}
