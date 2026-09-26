package zettai.ddt

import com.ubertob.pesticide.core.DdtActions
import com.ubertob.pesticide.core.DdtProtocol
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoList
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
}
