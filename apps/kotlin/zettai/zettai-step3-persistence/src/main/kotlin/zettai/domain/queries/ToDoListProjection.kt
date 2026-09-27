package zettai.domain.queries

import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoStatus
import zettai.domain.User
import zettai.domain.events.ItemAdded
import zettai.domain.events.ItemStatusChanged
import zettai.domain.events.ListCreated
import zettai.domain.events.ToDoListEvent

/**
 * 表示のためのモデル。
 *
 * ドメインの状態（ToDoListState）とは別に持つ。画面が欲しい形は、
 * 業務ルールを判断するために必要な形とは違う。
 */
data class ToDoListRow(
    val listName: ListName,
    val itemCount: Int,
    val doneCount: Int
)

/**
 * イベントから表示用のモデルを作る射影。
 *
 * 第 5 章の畳み込みと同じ形。畳み込む先が状態ではなく表示用のモデルになっただけ。
 */
data class ToDoListProjection(
    private val rows: Map<Pair<User, ListName>, ToDoListRow>,
    private val items: Map<Pair<User, ListName>, List<ToDoItem>>
) {
    fun listsFor(user: User): List<ToDoListRow> =
        rows.filterKeys { it.first == user }.values.sortedBy { it.listName.name }

    fun itemsFor(user: User, listName: ListName): List<ToDoItem>? = items[user to listName]

    /** 射影を別の形に変換する。ファンクタとして振る舞う。 */
    fun <T> map(f: (ToDoListRow) -> T): List<T> = rows.values.sortedBy { it.listName.name }.map(f)

    /** 1 つの出来事を適用する。 */
    fun project(event: ToDoListEvent): ToDoListProjection {
        val key = event.user to event.listName

        return when (event) {
            is ListCreated -> copy(
                rows = rows + (key to ToDoListRow(event.listName, itemCount = 0, doneCount = 0)),
                items = items + (key to emptyList())
            )

            is ItemAdded -> withItems(key, (items[key] ?: return this) + event.item)

            is ItemStatusChanged -> {
                val current = items[key] ?: return this

                withItems(key, current.map { it.withStatusIfMatches(event) })
            }
        }
    }

    private fun withItems(key: Pair<User, ListName>, updated: List<ToDoItem>): ToDoListProjection =
        copy(
            rows = rows + (key to summarize(key.second, updated)),
            items = items + (key to updated)
        )

    companion object {
        val empty = ToDoListProjection(emptyMap(), emptyMap())
    }
}

private fun summarize(listName: ListName, items: List<ToDoItem>): ToDoListRow =
    ToDoListRow(
        listName = listName,
        itemCount = items.size,
        doneCount = items.count { it.status == ToDoStatus.Done }
    )

private fun ToDoItem.withStatusIfMatches(event: ItemStatusChanged): ToDoItem =
    if (description == event.description) copy(status = event.newStatus) else this

/** 出来事の列を射影に畳み込む。 */
fun List<ToDoListEvent>.projectFrom(from: ToDoListProjection): ToDoListProjection =
    fold(from) { projection, event -> projection.project(event) }
