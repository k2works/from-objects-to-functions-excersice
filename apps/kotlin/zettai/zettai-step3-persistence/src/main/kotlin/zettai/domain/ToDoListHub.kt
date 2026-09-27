package zettai.domain

import zettai.domain.commands.ToDoListCommand
import zettai.domain.commands.handle
import zettai.domain.events.ToDoListEvent
import zettai.domain.events.ToDoListState
import zettai.domain.queries.ToDoListProjection
import zettai.domain.queries.ToDoListRow
import zettai.fp.ListNotFound
import zettai.fp.Outcome
import zettai.fp.ZettaiError
import zettai.fp.asFailure
import zettai.fp.asSuccess

/** 現在の状態を取り出す。コマンド側が業務ルールを判断するために使う。 */
typealias StateFetcher = () -> ToDoListState

/** 表示用の射影を取り出す。クエリ側が使う。 */
typealias ProjectionFetcher = () -> ToDoListProjection

/** 起きた出来事を保存する。 */
typealias EventPersister = (List<ToDoListEvent>) -> Outcome<ZettaiError, Unit>

/**
 * ドメインの入口。
 *
 * コマンド側とクエリ側で別の経路を持つ（CQRS）。
 * コマンド側は状態を見て業務ルールを判断し、クエリ側は射影を見て表示に答える。
 */
class ToDoListHub(
    private val fetchState: StateFetcher,
    private val fetchProjection: ProjectionFetcher,
    private val persist: EventPersister
) {
    // --- クエリ側 ---

    fun itemsFor(user: User, listName: ListName): Outcome<ZettaiError, List<ToDoItem>> =
        fetchProjection().itemsFor(user, listName)?.asSuccess()
            ?: ListNotFound("${listName.name} が見つかりません").asFailure()

    fun listsFor(user: User): Outcome<ZettaiError, List<ToDoListRow>> = fetchProjection().listsFor(user).asSuccess()

    // --- コマンド側 ---

    /**
     * コマンドを処理する。
     *
     * 拒否されたら失敗を返す。空のリストを返していた頃は、
     * 「何も起きなかった」と「拒否された」を呼び出し側が区別できなかった。
     */
    fun handle(command: ToDoListCommand): Outcome<ZettaiError, List<ToDoListEvent>> {
        val events = handle(command, fetchState())

        if (events.isEmpty()) return command.rejected()

        return persist(events).map { events }
    }
}

private fun ToDoListCommand.rejected(): Outcome<ZettaiError, Nothing> =
    ListNotFound("${listName.name} に対する $this は実行できません").asFailure()
