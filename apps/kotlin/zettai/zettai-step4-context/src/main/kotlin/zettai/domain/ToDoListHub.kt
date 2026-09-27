package zettai.domain

import zettai.domain.commands.ToDoListCommand
import zettai.domain.commands.handle
import zettai.domain.events.ToDoListEvent
import zettai.domain.events.ToDoListState
import zettai.domain.queries.ToDoListProjection
import zettai.domain.queries.ToDoListRow
import zettai.fp.ContextReader
import zettai.fp.ListNotFound
import zettai.fp.Outcome
import zettai.fp.ZettaiError
import zettai.fp.pure

/** 文脈がある状態で実行できる計算。第 10 章でポートの戻り値をこの形にした。 */
typealias HubAction<T> = ContextReader<TxContext, T>

/** 現在の状態を取り出す。コマンド側が業務ルールを判断するために使う。 */
typealias StateFetcher = () -> HubAction<ToDoListState>

/** 表示用の射影を取り出す。クエリ側が使う。 */
typealias ProjectionFetcher = () -> HubAction<ToDoListProjection>

/** 起きた出来事を保存する。 */
typealias EventPersister = (List<ToDoListEvent>) -> HubAction<Unit>

/**
 * ドメインの入口。
 *
 * コマンド側とクエリ側で別の経路を持つ（CQRS）。
 * 第 10 章でポートの戻り値を HubAction にした。実行を後回しにすることで、
 * 複数の操作が同じ文脈（接続）を受け取り、1 つのトランザクションになる。
 */
class ToDoListHub(
    private val fetchState: StateFetcher,
    private val fetchProjection: ProjectionFetcher,
    private val persist: EventPersister
) {
    // --- クエリ側 ---

    fun itemsFor(user: User, listName: ListName): HubAction<Outcome<ZettaiError, List<ToDoItem>>> =
        fetchProjection().map { projection ->
            projection.itemsFor(user, listName)
                ?.let(::success)
                ?: failure(ListNotFound("${listName.name} が見つかりません"))
        }

    fun listsFor(user: User): HubAction<Outcome<ZettaiError, List<ToDoListRow>>> =
        fetchProjection().map { success(it.listsFor(user)) }

    // --- コマンド側 ---

    /**
     * コマンドを処理する。
     *
     * 状態の取得と出来事の保存を flatMap で繋ぐので、同じ文脈で実行される。
     * 呼び出し側が runInTransaction で実行すれば、まとめてコミットされる。
     */
    fun handle(command: ToDoListCommand): HubAction<Outcome<ZettaiError, List<ToDoListEvent>>> =
        fetchState().flatMap { state ->
            val events = handle(command, state)

            if (events.isEmpty()) {
                pure(failure(command.rejected()))
            } else {
                persist(events).map { success(events) }
            }
        }
}

private fun <T> success(value: T): Outcome<ZettaiError, T> = zettai.fp.Success(value)

private fun failure(error: ZettaiError): Outcome<ZettaiError, Nothing> = zettai.fp.Failure(error)

private fun ToDoListCommand.rejected(): ZettaiError =
    ListNotFound("${listName.name} に対する $this は実行できません")
