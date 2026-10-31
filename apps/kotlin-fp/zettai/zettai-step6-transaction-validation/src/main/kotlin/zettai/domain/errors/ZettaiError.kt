package zettai.domain.errors

import arrow.core.NonEmptyList
import zettai.domain.ListName
import zettai.domain.StreamId
import zettai.domain.ToDoStatus
import zettai.domain.User
import zettai.domain.validation.FieldProblem

sealed interface ZettaiError {
    val message: String
}

data class ListNotFound(
    val user: User,
    val listName: ListName,
) : ZettaiError {
    override val message: String = "ToDo list not found: ${user.name}/${listName.name}"
}

data class ListAlreadyExists(
    val user: User,
    val listName: ListName,
) : ZettaiError {
    override val message: String = "ToDo list already exists: ${user.name}/${listName.name}"
}

data class ItemNotFound(
    val user: User,
    val listName: ListName,
    val description: String,
) : ZettaiError {
    override val message: String =
        "ToDo item not found: ${user.name}/${listName.name}/$description"
}

/** 永続化に関わる失敗。 */
sealed interface StoreError : ZettaiError

/**
 * 永続化の失敗。原因は `reason` に持ち、`message` には含めない。
 * `message` は利用者向けの response の本文になり、接続先などの内部情報を漏らさないため。
 */
data class PersistenceFailure(
    val reason: String,
) : StoreError {
    override val message: String = "Persistence failure"
}

/** 読んだ後に、別の command が同じ stream へ追記した。検出した stream を持つ。 */
data class ConcurrentModification(
    val streams: List<StreamId>,
) : StoreError {
    override val message: String =
        "Concurrent modification: ${streams.joinToString { "${it.user.name}/${it.listName.name}" }}"
}

/** 入力の検証に失敗した。誤りは `problems` に集まり、`message` には入力の値を含めない。 */
data class InvalidInput(
    val problems: NonEmptyList<FieldProblem>,
) : ZettaiError {
    override val message: String = "Invalid input"
}

data class InvalidTransition(
    val description: String,
    val from: ToDoStatus,
    val to: ToDoStatus,
) : ZettaiError {
    override val message: String = "Invalid transition for $description: $from -> $to"
}
