package zettai.domain.commands

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import zettai.domain.canTransitionTo
import zettai.domain.errors.InvalidTransition
import zettai.domain.errors.ItemNotFound
import zettai.domain.errors.ListAlreadyExists
import zettai.domain.errors.ListNotFound
import zettai.domain.errors.ZettaiError
import zettai.domain.events.ItemAdded
import zettai.domain.events.ItemStatusChanged
import zettai.domain.events.ListCreated
import zettai.domain.events.ToDoListEvent
import zettai.domain.events.ToDoListState

fun handle(
    command: ToDoListCommand,
    state: ToDoListState,
): Either<ZettaiError, List<ToDoListEvent>> =
    either {
        when (command) {
            is CreateToDoList -> {
                ensure(state.listFor(command.user, command.listName) == null) {
                    ListAlreadyExists(command.user, command.listName)
                }
                listOf(ListCreated(command.user, command.listName))
            }

            is AddToDoItem -> {
                ensureNotNull(state.listFor(command.user, command.listName)) {
                    ListNotFound(command.user, command.listName)
                }
                listOf(ItemAdded(command.user, command.listName, command.item))
            }

            is ChangeItemStatus -> {
                val list = ensureNotNull(state.listFor(command.user, command.listName)) {
                    ListNotFound(command.user, command.listName)
                }
                val item = ensureNotNull(
                    list.items.firstOrNull { it.description == command.description },
                ) {
                    ItemNotFound(command.user, command.listName, command.description)
                }
                ensure(item.status.canTransitionTo(command.newStatus)) {
                    InvalidTransition(command.description, item.status, command.newStatus)
                }
                listOf(
                    ItemStatusChanged(
                        command.user,
                        command.listName,
                        command.description,
                        command.newStatus,
                    ),
                )
            }
        }
    }
