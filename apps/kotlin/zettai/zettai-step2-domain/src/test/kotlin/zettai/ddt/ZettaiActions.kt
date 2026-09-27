package zettai.ddt

import com.ubertob.pesticide.core.DdtActions
import com.ubertob.pesticide.core.DdtProtocol
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoList
import zettai.domain.ToDoStatus
import zettai.domain.User

/**
 * 受け入れテストが使える操作の一覧。
 *
 * ここに HTTP の言葉は出てこない。テストは「ToDo リストを見る」としか言わず、
 * それを HTTP 経由でやるのかドメインを直接呼ぶのかは実装側が決める。
 */
interface ZettaiActions : DdtActions<DdtProtocol> {
    fun setUp(user: User, list: ToDoList)

    fun getToDoList(user: User, listName: ListName): List<ToDoItem>?

    /** 出来事としてリストを作る（第 5 章）。 */
    fun createList(user: User, listName: ListName)

    /** コマンドで項目を追加する（第 6 章）。 */
    fun addItem(user: User, listName: ListName, item: ToDoItem)

    /** コマンドで項目の状態を変える（第 6 章）。失敗したら false。 */
    fun changeItemStatus(user: User, listName: ListName, description: String, status: ToDoStatus): Boolean

    /** 失敗の理由を取り出す（第 7 章）。成功したら null。 */
    fun errorFor(user: User, listName: ListName): String?
}
