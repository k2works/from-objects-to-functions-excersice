package zettai.ddt

import com.ubertob.pesticide.core.DdtProtocol
import com.ubertob.pesticide.core.DomainSetUp
import com.ubertob.pesticide.core.Http
import com.ubertob.pesticide.core.Ready
import java.time.LocalDate
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoList
import zettai.domain.ToDoListHub
import zettai.domain.ToDoStatus
import zettai.domain.User
import zettai.domain.commands.AddToDoItem
import zettai.domain.commands.ChangeItemStatus
import zettai.domain.commands.CreateToDoList
import zettai.domain.events.ItemAdded
import zettai.domain.events.ListCreated
import zettai.domain.events.ToDoListEvent
import zettai.domain.events.ToDoListState
import zettai.domain.events.replayFrom
import zettai.domain.queries.ToDoListProjection
import zettai.domain.queries.projectFrom
import zettai.fp.Success
import zettai.fp.pure
import zettai.web.Zettai

/** HTTP 経由の経路。レスポンスの HTML から項目を取り出す。 */
class HttpActions : ZettaiActions {
    override val protocol: DdtProtocol = Http("http4k")

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

    override fun getToDoList(user: User, listName: ListName): List<ToDoItem>? {
        val zettai = Zettai(hub(), ::runWithoutContext)
        val response = zettai(Request(Method.GET, "/todo/${user.name}/${listName.name}"))

        if (response.status != Status.OK) return null

        return response.bodyString().extractItems()
    }
}

private val ROW = Regex("""<tr><td>(.*?)</td><td>(.*?)</td><td>(.*?)</td></tr>""")

/** HTML の行から項目を復元する。表示に出ているものだけが読み取れる。 */
private fun String.extractItems(): List<ToDoItem> =
    ROW.findAll(this)
        .map { match ->
            val (description, status, dueDate) = match.destructured
            ToDoItem(
                description = description,
                dueDate = dueDate.takeIf { it.isNotBlank() }?.let(LocalDate::parse),
                status = ToDoStatus.valueOf(status)
            )
        }
        .toList()
