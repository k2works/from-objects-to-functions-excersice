package zettai.domain

import java.time.LocalDate
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNull
import zettai.domain.commands.CreateToDoList
import zettai.domain.events.ListCreated
import zettai.domain.events.ToDoListEvent
import zettai.domain.events.ToDoListState
import zettai.domain.events.replayFrom
import zettai.domain.queries.ToDoListProjection
import zettai.domain.queries.projectFrom
import zettai.fp.Failure
import zettai.fp.PersistenceError
import zettai.fp.Success
import zettai.fp.asFailure
import zettai.fp.asSuccess

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
            fetchState = { events.replayFrom(ToDoListState.empty) },
            fetchProjection = { events.projectFrom(ToDoListProjection.empty) },
            persist = { newEvents ->
                events += newEvents
                Unit.asSuccess()
            }
        )

    @Test
    fun `コマンドを処理すると出来事が残る`() {
        val events = mutableListOf<ToDoListEvent>()
        val hub = hubWith(events)

        val expected: List<ToDoListEvent> = listOf(ListCreated(user, listName))

        expectThat(hub.handle(CreateToDoList(user, listName))).isEqualTo(Success(expected))
        expectThat(events.toList()).isEqualTo(expected)
    }

    @Test
    fun `拒否されたコマンドは失敗を返す`() {
        val hub = hubWith(mutableListOf<ToDoListEvent>(ListCreated(user, listName)))

        expectThat(hub.handle(CreateToDoList(user, listName))).isA<Failure<*>>()
    }

    @Test
    fun `クエリ側は射影を見る`() {
        val events = mutableListOf<ToDoListEvent>()
        val hub = hubWith(events)
        hub.handle(CreateToDoList(user, listName))

        expectThat(hub.itemsFor(user, listName)).isEqualTo(Success(emptyList<ToDoItem>()))
    }

    @Test
    fun `見つからなければ失敗を返す`() {
        val hub = hubWith(mutableListOf<ToDoListEvent>())

        expectThat(hub.itemsFor(user, ListName("missing"))).isA<Failure<*>>()
    }

    @Test
    fun `保存が失敗したらコマンドも失敗する`() {
        val hub = ToDoListHub(
            fetchState = { ToDoListState.empty },
            fetchProjection = { ToDoListProjection.empty },
            persist = { PersistenceError("書き込めません").asFailure() }
        )

        expectThat(hub.handle(CreateToDoList(user, listName))).isA<Failure<*>>()
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
