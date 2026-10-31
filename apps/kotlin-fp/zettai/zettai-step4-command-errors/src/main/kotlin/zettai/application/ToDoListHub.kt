package zettai.application

import arrow.core.Either
import arrow.core.raise.either
import zettai.domain.ListName
import zettai.domain.ToDoList
import zettai.domain.User
import zettai.domain.commands.ToDoListCommand
import zettai.domain.commands.handle
import zettai.domain.errors.ZettaiError
import zettai.domain.events.ToDoListEvent
import zettai.domain.events.ToDoListState

typealias ToDoListFetcher =
    suspend (User, ListName) -> Either<ZettaiError, ToDoList>

typealias StateFetcher = suspend () -> ToDoListState

typealias EventPersister = suspend (List<ToDoListEvent>) -> Unit

data class ToDoListHub(
    val getList: ToDoListFetcher,
    val execute: suspend (ToDoListCommand) -> Either<ZettaiError, List<ToDoListEvent>>,
)

fun todoListHub(
    fetchList: ToDoListFetcher,
    fetchState: StateFetcher,
    persist: EventPersister,
): ToDoListHub =
    ToDoListHub(
        getList = fetchList,
        execute = { command ->
            either {
                val events = handle(command, fetchState()).bind()
                persist(events)
                events
            }
        },
    )
