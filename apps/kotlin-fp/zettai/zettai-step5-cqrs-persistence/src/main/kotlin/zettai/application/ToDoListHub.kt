package zettai.application

import arrow.core.Either
import arrow.core.raise.either
import zettai.domain.ListName
import zettai.domain.ToDoList
import zettai.domain.User
import zettai.domain.commands.ToDoListCommand
import zettai.domain.commands.handle
import zettai.domain.errors.ListNotFound
import zettai.domain.errors.PersistenceFailure
import zettai.domain.errors.ZettaiError
import zettai.domain.events.ToDoListEvent
import zettai.domain.events.replayFrom
import zettai.domain.queries.ToDoListRow
import zettai.domain.queries.projectFrom

/** command 側が読む。判断に必要な集約のイベントだけを返す。起こり得る失敗は永続化の失敗だけ。 */
typealias AggregateEventReader =
    suspend (User, ListName) -> Either<PersistenceFailure, List<ToDoListEvent>>

/** query 側が読む。ユーザーのイベントを返し、射影の入力になる。 */
typealias UserEventReader = suspend (User) -> Either<PersistenceFailure, List<ToDoListEvent>>

typealias EventPersister = suspend (List<ToDoListEvent>) -> Either<PersistenceFailure, Unit>

typealias ListQuery = suspend (User, ListName) -> Either<ZettaiError, ToDoList>

typealias RowsQuery = suspend (User) -> Either<ZettaiError, List<ToDoListRow>>

typealias CommandExecutor = suspend (ToDoListCommand) -> Either<ZettaiError, List<ToDoListEvent>>

/**
 * command 側（execute）と query 側（getList、listRows）は別の関数として現れる。
 * どちらも event store を唯一の源とし、query は毎回イベントから射影する。
 */
data class ToDoListHub(
    val getList: ListQuery,
    val listRows: RowsQuery,
    val execute: CommandExecutor,
)

fun todoListHub(
    readAggregate: AggregateEventReader,
    readUser: UserEventReader,
    persist: EventPersister,
): ToDoListHub =
    ToDoListHub(
        getList = { user, listName ->
            either {
                readAggregate(user, listName).bind()
                    .replayFrom()
                    .listFor(user, listName)
                    ?: raise(ListNotFound(user, listName))
            }
        },
        listRows = { user -> either { projectFrom(user, readUser(user).bind()) } },
        execute = { command ->
            either {
                val state = readAggregate(command.user, command.listName).bind().replayFrom()
                val events = handle(command, state).bind()
                persist(events).bind()
                events
            }
        },
    )
