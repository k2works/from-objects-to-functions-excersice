package zettai.domain.queries

import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoStatus
import zettai.domain.User
import zettai.domain.events.ItemAdded
import zettai.domain.events.ItemStatusChanged
import zettai.domain.events.ListCreated
import zettai.domain.events.ToDoListEvent

/** 一覧画面が欲しい形。業務ルールを判断する `ToDoListState` とは別に持つ。 */
data class ToDoListRow(
    val listName: ListName,
    val itemCount: Int,
    val doneCount: Int,
)

/**
 * イベントから表示用の行を作る射影。`replayFrom` と同じ `fold` で、畳み込み先が行になる。
 * 行はリストの作成順に並ぶ。
 */
fun projectFrom(user: User, events: List<ToDoListEvent>): List<ToDoListRow> =
    events
        .filter { it.user == user }
        .fold(emptyMap<ListName, List<ToDoItem>>()) { lists, event -> lists.step(event) }
        .map { (listName, items) ->
            ToDoListRow(
                listName = listName,
                itemCount = items.size,
                doneCount = items.count { it.status == ToDoStatus.Done },
            )
        }

private fun Map<ListName, List<ToDoItem>>.step(event: ToDoListEvent): Map<ListName, List<ToDoItem>> =
    when (event) {
        is ListCreated -> this + (event.listName to emptyList())

        is ItemAdded -> {
            val items = this[event.listName]
            if (items == null) this else this + (event.listName to (items + event.item))
        }

        is ItemStatusChanged -> {
            val items = this[event.listName]
            val target = items?.indexOfFirst { it.description == event.description } ?: -1
            if (items == null || target < 0) {
                this
            } else {
                this + (
                    event.listName to items.mapIndexed { index, item ->
                        if (index == target) item.copy(status = event.newStatus) else item
                    }
                    )
            }
        }
    }
