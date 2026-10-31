package zettai.application

import arrow.core.Either
import arrow.core.raise.either
import zettai.domain.ListName
import zettai.domain.StreamId
import zettai.domain.ToDoList
import zettai.domain.User
import zettai.domain.commands.ToDoListCommand
import zettai.domain.commands.handle
import zettai.domain.errors.ListNotFound
import zettai.domain.errors.PersistenceFailure
import zettai.domain.errors.StoreError
import zettai.domain.errors.ZettaiError
import zettai.domain.events.ToDoListEvent
import zettai.domain.events.replayFrom
import zettai.domain.queries.ToDoListRow
import zettai.domain.queries.projectFrom

/** query 側が読む。transaction の外で、集約のイベントだけを返す。 */
typealias AggregateEventReader =
    suspend (User, ListName) -> Either<PersistenceFailure, List<ToDoListEvent>>

/** query 側が読む。ユーザーのイベントを返し、射影の入力になる。 */
typealias UserEventReader = suspend (User) -> Either<PersistenceFailure, List<ToDoListEvent>>

/** stream のイベントと、その版。版は、追記済みの event の件数に等しい。 */
data class StreamEvents(val events: List<ToDoListEvent>, val version: Int)

/**
 * transaction の境界。block に環境 [Tx] を渡して実行し、`Right` なら commit、`Left` や例外なら rollback する。
 * [Tx] を持たない場所からは、下の読み出しと追記を呼べない。
 */
typealias Transaction<Tx> =
    suspend (suspend (Tx) -> Either<ZettaiError, List<ToDoListEvent>>) -> Either<ZettaiError, List<ToDoListEvent>>

/** transaction の中で、stream を版つきで読む。 */
typealias StreamReader<Tx> = suspend (Tx, StreamId) -> Either<PersistenceFailure, StreamEvents>

/** transaction の中で、触れる stream ごとに期待する版を渡して追記する。版が古ければ `ConcurrentModification`。 */
typealias StreamAppender<Tx> =
    suspend (Tx, Map<StreamId, Int>, List<ToDoListEvent>) -> Either<StoreError, Unit>

/** 追記だけの event store。adapter が実装して返し、Hub が使う。 */
data class EventStore<Tx>(
    val readAggregate: AggregateEventReader,
    val readUser: UserEventReader,
    val transaction: Transaction<Tx>,
    val readStream: StreamReader<Tx>,
    val append: StreamAppender<Tx>,
)

typealias ListQuery = suspend (User, ListName) -> Either<ZettaiError, ToDoList>

typealias RowsQuery = suspend (User) -> Either<ZettaiError, List<ToDoListRow>>

typealias CommandExecutor = suspend (ToDoListCommand) -> Either<ZettaiError, List<ToDoListEvent>>

/**
 * command 側（execute）と query 側（getList、listRows）は別の関数として現れる。
 * [Tx] は組み立てるときにだけ現れ、組み立てた Hub の型には出ない。
 */
data class ToDoListHub(
    val getList: ListQuery,
    val listRows: RowsQuery,
    val execute: CommandExecutor,
)

fun <Tx> todoListHub(store: EventStore<Tx>): ToDoListHub =
    ToDoListHub(
        getList = { user, listName ->
            either {
                store.readAggregate(user, listName).bind()
                    .replayFrom()
                    .listFor(user, listName)
                    ?: raise(ListNotFound(user, listName))
            }
        },
        listRows = { user -> either { projectFrom(user, store.readUser(user).bind()) } },
        execute = { command ->
            store.transaction { tx ->
                either {
                    val read = command.streams.associateWith { store.readStream(tx, it).bind() }
                    val state = read.values.flatMap { it.events }.replayFrom()
                    val events = handle(command, state).bind()
                    store.append(tx, read.mapValues { it.value.version }, events).bind()
                    events
                }
            }
        },
    )
