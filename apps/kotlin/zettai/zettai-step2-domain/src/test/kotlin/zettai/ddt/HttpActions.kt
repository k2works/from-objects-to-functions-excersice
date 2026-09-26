package zettai.ddt

import com.ubertob.pesticide.core.DdtProtocol
import com.ubertob.pesticide.core.DomainSetUp
import com.ubertob.pesticide.core.Http
import com.ubertob.pesticide.core.Ready
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoList
import zettai.domain.ToDoListHub
import zettai.domain.events.ItemAdded
import zettai.domain.events.ListCreated
import zettai.domain.events.ToDoListEvent
import zettai.domain.events.ToDoListState
import zettai.domain.events.replayFrom
import zettai.domain.User
import zettai.web.Zettai
import zettai.web.inMemoryFetcher

/** HTTP 経由の経路。レスポンスの HTML から項目を取り出す。 */
class HttpActions : ZettaiActions {
    override val protocol: DdtProtocol = Http("http4k")

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
        events += ListCreated(user, listName)
    }

    override fun getToDoList(user: User, listName: ListName): List<ToDoItem>? {
        val zettai = Zettai(ToDoListHub { u, l -> state.listFor(u, l) })
        val response = zettai(Request(Method.GET, "/todo/${user.name}/${listName.name}"))

        if (response.status != Status.OK) return null

        return response.bodyString().extractItems()
    }
}

private val CELL = Regex("""<td>(.*?)</td>""")

private fun String.extractItems(): List<ToDoItem> =
    CELL.findAll(this).map { ToDoItem(it.groupValues[1]) }.toList()
