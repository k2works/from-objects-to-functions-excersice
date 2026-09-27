package zettai.domain.commands

import zettai.domain.canTransitionTo
import zettai.domain.events.ItemAdded
import zettai.domain.events.ItemStatusChanged
import zettai.domain.events.ListCreated
import zettai.domain.events.ListRenamed
import zettai.domain.events.ToDoListEvent
import zettai.domain.events.ToDoListState

/**
 * コマンドと現在の状態から、起こったことを決める。
 *
 * これが関数型のステートマシン。状態を書き換えるのではなく、
 * 「この状態でこのコマンドなら、この出来事が起きる」を返す。
 * 許されない操作では出来事が起きないので、空のリストを返す。
 */
fun handle(command: ToDoListCommand, state: ToDoListState): List<ToDoListEvent> =
    when (command) {
        is CreateToDoList ->
            if (state.listFor(command.user, command.listName) != null) {
                emptyList()
            } else {
                listOf(ListCreated(command.user, command.listName))
            }

        is AddToDoItem ->
            if (state.listFor(command.user, command.listName) == null) {
                emptyList()
            } else {
                listOf(ItemAdded(command.user, command.listName, command.item))
            }

        is RenameToDoList ->
            if (state.listFor(command.user, command.listName) == null ||
                state.listFor(command.user, command.newName) != null
            ) {
                emptyList()
            } else {
                listOf(ListRenamed(command.user, command.listName, command.newName))
            }

        is ChangeItemStatus -> {
            val item = state.listFor(command.user, command.listName)
                ?.items
                ?.firstOrNull { it.description == command.description }

            if (item == null || !item.status.canTransitionTo(command.newStatus)) {
                emptyList()
            } else {
                listOf(ItemStatusChanged(command.user, command.listName, command.description, command.newStatus))
            }
        }
    }
