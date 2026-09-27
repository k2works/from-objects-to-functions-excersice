package zettai.domain

import java.time.LocalDate
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNull
import zettai.domain.HubAction
import zettai.domain.InMemoryContext
import zettai.domain.commands.CreateToDoList
import zettai.domain.events.ListCreated
import zettai.domain.events.ToDoListEvent
import zettai.domain.events.ToDoListState
import zettai.domain.events.replayFrom
import zettai.domain.queries.ToDoListProjection
import zettai.domain.queries.projectFrom
import zettai.fp.Failure
import zettai.fp.Success
import zettai.fp.pure

/**
 * ハブのテスト。
 *
 * アダプタの実装クラスを 1 つも用意していない。必要な振る舞いはラムダで書く。
 */
class ToDoListHubTest {
    private val user = User("uberto")
    private val listName = ListName("book")

    /** 出来事を溜めるだけのハブ。コマンド側とクエリ側の両方が同じ出来事を見る。 */
    private fun hubWith(events: MutableList<ToDoListEvent>) =
        ToDoListHub(
            fetchState = { pure(events.replayFrom(ToDoListState.empty)) },
            fetchProjection = { pure(events.projectFrom(ToDoListProjection.empty)) },
            persist = { newEvents -> pure(events.addAll(newEvents).let { }) }
        )

    /** 文脈を使わずに実行する。インメモリなので接続は不要。 */
    private fun <T> run(action: HubAction<T>): T = action.runWith(InMemoryContext)

    @Test
    fun `コマンドを処理すると出来事が残る`() {
        val events = mutableListOf<ToDoListEvent>()
        val hub = hubWith(events)

        val expected: List<ToDoListEvent> = listOf(ListCreated(user, listName))

        expectThat(run(hub.handle(CreateToDoList(user, listName)))).isEqualTo(Success(expected))
        expectThat(events.toList()).isEqualTo(expected)
    }

    @Test
    fun `拒否されたコマンドは失敗を返す`() {
        val hub = hubWith(mutableListOf<ToDoListEvent>(ListCreated(user, listName)))

        expectThat(run(hub.handle(CreateToDoList(user, listName)))).isA<Failure<*>>()
    }

    @Test
    fun `クエリ側は射影を見る`() {
        val events = mutableListOf<ToDoListEvent>()
        val hub = hubWith(events)
        run(hub.handle(CreateToDoList(user, listName)))

        expectThat(run(hub.itemsFor(user, listName))).isEqualTo(Success(emptyList<ToDoItem>()))
    }

    @Test
    fun `見つからなければ失敗を返す`() {
        val hub = hubWith(mutableListOf<ToDoListEvent>())

        expectThat(run(hub.itemsFor(user, ListName("missing")))).isA<Failure<*>>()
    }

    @Test
    fun `保存が失敗したら実行時に伝わる`() {
        val hub = ToDoListHub(
            fetchState = { pure(ToDoListState.empty) },
            fetchProjection = { pure(ToDoListProjection.empty) },
            persist = { error("書き込めません") }
        )

        // 第 10 章でポートが HubAction を返す形になったので、保存の失敗は
        // 実行時（runInTransaction / runOn）に捕らえて Outcome に変える
        expectThat(runCatching { run(hub.handle(CreateToDoList(user, listName))) }.isFailure).isEqualTo(true)
    }
}

class ToDoItemTest {
    @Test
    fun `項目は既定で Todo 状態`() {
        expectThat(ToDoItem("write chapter").status).isEqualTo(ToDoStatus.Todo)
    }

    @Test
    fun `期限は任意`() {
        expectThat(ToDoItem("write chapter").dueDate).isNull()
        expectThat(ToDoItem("write chapter", LocalDate.of(2026, 12, 31)).dueDate)
            .isEqualTo(LocalDate.of(2026, 12, 31))
    }
}
