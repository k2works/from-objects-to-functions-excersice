package zettai

import org.http4k.server.Jetty
import org.http4k.server.asServer
import zettai.domain.HubAction
import zettai.domain.InMemoryContext
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoListHub
import zettai.domain.ToDoStatus
import zettai.domain.TxContext
import zettai.domain.User
import zettai.domain.events.ItemAdded
import zettai.domain.events.ListCreated
import zettai.domain.events.ToDoListEvent
import zettai.domain.events.ToDoListState
import zettai.domain.events.replayFrom
import zettai.domain.queries.ToDoListProjection
import zettai.domain.queries.projectFrom
import zettai.fp.pure
import zettai.logger.LogContext
import zettai.logger.logged
import zettai.logger.stdoutLogger
import zettai.web.Zettai

/**
 * アプリケーションを起動する。
 *
 * 第 12 章の構造化ログは、動かして見ないと価値が伝わらない。
 * ブラウザでリストを開くと、標準出力に 1 行 1 JSON のログが出る。
 *
 * 出来事の保存先はメモリなので、止めると消える。PostgreSQL 経由の経路は
 * 結合テスト（DDT の 3 経路目）で確かめている。
 */
fun main() {
    val logger = stdoutLogger()
    val events = mutableListOf<ToDoListEvent>()

    events += sampleEvents()

    // 出来事を読む計算。ログは包むだけなので、ハブに渡す型は変わらない
    fun readEvents(operation: String): HubAction<List<ToDoListEvent>> =
        pure<TxContext, List<ToDoListEvent>>(events.toList()).logged(logger, LogContext(operation))

    val hub = ToDoListHub(
        fetchState = { readEvents("状態を読む").map { it.replayFrom(ToDoListState.empty) } },
        fetchProjection = { readEvents("射影を読む").map { it.projectFrom(ToDoListProjection.empty) } },
        persist = { newEvents ->
            val context = LogContext("出来事を保存する", mapOf("件数" to newEvents.size.toString()))

            pure<TxContext, Unit>(events.addAll(newEvents).let { }).logged(logger, context)
        }
    )

    val zettai = Zettai(hub, ::runWithoutContext)

    println("http://localhost:8080/todo/uberto/book を開いてください")

    zettai.asServer(Jetty(8080)).start()
}

/** 文脈を使わずに実行する。インメモリなので接続が要らない。 */
private fun <T> runWithoutContext(action: HubAction<T>): T = action.runWith(InMemoryContext)

/** 開いてすぐ中身が見えるように、出来事をいくつか用意する。 */
private fun sampleEvents(): List<ToDoListEvent> {
    val user = User("uberto")
    val listName = ListName("book")

    return listOf(
        ListCreated(user, listName),
        ItemAdded(user, listName, ToDoItem("write chapter", status = ToDoStatus.Done)),
        ItemAdded(user, listName, ToDoItem("review PR")),
        ItemAdded(user, listName, ToDoItem("publish book"))
    )
}
