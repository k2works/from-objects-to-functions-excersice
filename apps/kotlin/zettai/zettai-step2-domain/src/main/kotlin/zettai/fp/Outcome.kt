package zettai.fp

/**
 * 成功か失敗かを表す型。
 *
 * null は「無い」ことしか表せないが、Outcome は「なぜ無いのか」を持てる。
 * 呼び出し側は失敗を無視できない。map で繋ぐと、失敗はそのまま流れていく。
 */
sealed interface Outcome<out E, out T> {

    /** 成功なら値を変換する。失敗ならそのまま流す。 */
    fun <U> map(f: (T) -> U): Outcome<E, U> =
        when (this) {
            is Success -> Success(f(value))
            is Failure -> this
        }

    /** 成功なら次の Outcome に繋ぐ。失敗ならそのまま流す。 */
    fun <F, U> transform(f: (T) -> Outcome<F, U>): Outcome<Any?, U> =
        when (this) {
            is Success -> f(value)
            is Failure -> this
        }

    /** 成功と失敗のどちらでも 1 つの値に畳み込む。 */
    fun <U> fold(onFailure: (E) -> U, onSuccess: (T) -> U): U =
        when (this) {
            is Success -> onSuccess(value)
            is Failure -> onFailure(error)
        }
}

data class Success<T>(val value: T) : Outcome<Nothing, T>

data class Failure<E>(val error: E) : Outcome<E, Nothing>

/**
 * 失敗なら代わりの値を返す。
 *
 * メンバー関数ではなく拡張関数にしている。共変な T を引数に取ると
 * @UnsafeVariance が必要になり、Failure（T = Nothing）で
 * 実行時に ClassCastException が起きる。実際に踏んだ。
 */
fun <E, T> Outcome<E, T>.orElse(default: T): T =
    when (this) {
        is Success -> value
        is Failure -> default
    }

/** 値を成功として包む。 */
fun <T> T.asSuccess(): Outcome<Nothing, T> = Success(this)

/** 理由を失敗として包む。 */
fun <E> E.asFailure(): Outcome<E, Nothing> = Failure(this)
