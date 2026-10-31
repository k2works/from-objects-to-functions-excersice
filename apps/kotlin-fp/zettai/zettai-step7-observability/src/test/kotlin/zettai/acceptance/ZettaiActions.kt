package zettai.acceptance

import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoList
import zettai.domain.User

/** 名前変更の結果。拒否の理由は、どの経路でも同じ文言で比べられるよう、文字列で持つ。 */
sealed interface RenameOutcome {
    data object Renamed : RenameOutcome

    data class Rejected(val reasons: List<String>) : RenameOutcome
}

/** 同じ scenario を異なる実行経路で動かすための操作。経路の差はここの外に出さない。 */
data class ZettaiActions(
    val name: String,
    val setUp: suspend (User, ToDoList) -> Unit,
    val getToDoList: suspend (User, ListName) -> List<ToDoItem>?,
    val rename: suspend (User, ListName, String) -> RenameOutcome,
)
