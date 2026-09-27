package zettai.fp

/**
 * Zettai で起きうる失敗。
 *
 * 「見つからない」で済ませず、理由を型で区別する。
 * 区別できないと、利用者に何を伝えればいいか決められない。
 */
sealed interface ZettaiError {
    val message: String
}

data class ListNotFound(override val message: String) : ZettaiError

data class ListAlreadyExists(override val message: String) : ZettaiError

data class ItemNotFound(override val message: String) : ZettaiError

data class InvalidTransition(override val message: String) : ZettaiError
