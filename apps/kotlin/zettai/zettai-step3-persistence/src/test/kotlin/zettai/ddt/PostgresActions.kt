package zettai.ddt

import com.ubertob.pesticide.core.DdtProtocol
import com.ubertob.pesticide.core.DomainSetUp
import com.ubertob.pesticide.core.Http
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
import zettai.domain.events.ToDoListState
import zettai.domain.events.replayFrom
import zettai.domain.queries.ToDoListProjection
import zettai.domain.queries.projectFrom
import zettai.fp.Success
import zettai.persistence.EntityId
import zettai.persistence.PostgresEventStore
import zettai.persistence.TestDatabase
import zettai.persistence.runOn

/**
 * PostgreSQL 経由の経路（第 9 章）。
 *
 * 出来事の保存先がデータベースになるだけで、シナリオは変わらない。
 * 3 経路とも同じシナリオが通ることが、永続化を入れても外から見た
 * 振る舞いが変わっていないことの証拠になる。
 */
class PostgresActions : ZettaiActions {
    override val protocol: DdtProtocol = Http("postgres")

    /** シナリオごとにテーブルを空にする。 */
    override fun prepare(): DomainSetUp {
        TestDatabase.reset()
        return Ready
    }

    override fun tearDown(): ZettaiActions = this

    override fun setUp(user: User, list: ToDoList) {
        createList(user, list.listName)
        list.items.forEach { addItem(user, list.listName, it) }
    }

    override fun createList(user: User, listName: ListName) {
        hub(user, listName).handle(CreateToDoList(user, listName))
    }

    override fun addItem(user: User, listName: ListName, item: ToDoItem) {
        hub(user, listName).handle(AddToDoItem(user, listName, item))
    }

    override fun changeItemStatus(
        user: User,
        listName: ListName,
        description: String,
        status: ToDoStatus
    ): Boolean = hub(user, listName).handle(ChangeItemStatus(user, listName, description, status)) is Success

    override fun getToDoList(user: User, listName: ListName): List<ToDoItem>? =
        hub(user, listName).itemsFor(user, listName).fold({ null }, { it })

    override fun errorFor(user: User, listName: ListName): String? =
        hub(user, listName).itemsFor(user, listName).fold({ it.message }, { null })

    /** ハブを組み立てる。出来事の保存先と読み出し元がデータベースになる。 */
    private fun hub(user: User, listName: ListName): ToDoListHub {
        val entityId = EntityId.of(user, listName)

        return ToDoListHub(
            fetchState = { storedEvents().replayFrom(ToDoListState.empty) },
            fetchProjection = { storedEvents().projectFrom(ToDoListProjection.empty) },
            persist = { events -> PostgresEventStore.append(entityId, events).runOn(TestDatabase::connect) }
        )
    }

    private fun storedEvents() =
        PostgresEventStore.readAll().runOn(TestDatabase::connect).fold({ emptyList() }, { it })
}
