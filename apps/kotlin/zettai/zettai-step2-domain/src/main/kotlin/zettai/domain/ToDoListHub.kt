package zettai.domain

import zettai.domain.commands.ToDoListCommand
import zettai.domain.commands.handle
import zettai.domain.events.ToDoListEvent
import zettai.domain.events.ToDoListState
import zettai.fp.ListNotFound
import zettai.fp.Outcome
import zettai.fp.ZettaiError
import zettai.fp.asFailure
import zettai.fp.asSuccess

/**
 * ToDo リストを取り出す。
 *
 * 第 7 章で戻り値を ToDoList? から Outcome に変えた。
 * 「見つからない」以外の失敗を表せなかったため。
 */
typealias ToDoListFetcher = (User, ListName) -> Outcome<ZettaiError, ToDoList>

/** 現在の状態を取り出す。 */
typealias StateFetcher = () -> ToDoListState

/** 起きた出来事を保存する。 */
typealias EventPersister = (List<ToDoListEvent>) -> Unit

/**
 * ドメインの入口。
 *
 * アダプタを関数の型で受け取る。インターフェースを定義しないので、
 * 呼ぶ側はラムダを渡すだけでよく、テスト用の実装クラスが要らない。
 */
class ToDoListHub(
    private val fetchList: ToDoListFetcher,
    private val fetchState: StateFetcher = { ToDoListState.empty },
    private val persist: EventPersister = { }
) {

    fun getList(user: User, listName: ListName): Outcome<ZettaiError, ToDoList> = fetchList(user, listName)

    /**
     * コマンドを処理する。
     *
     * 拒否されたら失敗を返す。空のリストを返していた頃は、
     * 「何も起きなかった」と「拒否された」を呼び出し側が区別できなかった。
     */
    fun handle(command: ToDoListCommand): Outcome<ZettaiError, List<ToDoListEvent>> {
        val events = handle(command, fetchState())

        return if (events.isEmpty()) {
            command.rejected()
        } else {
            persist(events)
            events.asSuccess()
        }
    }
}

private fun ToDoListCommand.rejected(): Outcome<ZettaiError, Nothing> =
    ListNotFound("${listName.name} に対する $this は実行できません").asFailure()
