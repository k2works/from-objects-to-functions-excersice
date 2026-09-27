package zettai.fp

/**
 * 検証の結果。
 *
 * Outcome（モナド）は最初の失敗で止まる。flatMap が「前の結果を使って次を決める」
 * ので、前が失敗したら次を実行できない。
 *
 * Validation は止まらない。複数の検証を独立に走らせ、失敗を全部集める。
 * 「前の結果を使う」ことを諦める代わりに、「全部集める」ことができる。
 */
sealed interface Validation<out T> {
    fun <U> map(f: (T) -> U): Validation<U> =
        when (this) {
            is Valid -> Valid(f(value))
            is Invalid -> this
        }
}

data class Valid<T>(val value: T) : Validation<T>

data class Invalid(val errors: List<String>) : Validation<Nothing> {
    constructor(error: String) : this(listOf(error))
}

/** 値を検証済みとして包む。 */
fun <T> T.asValid(): Validation<T> = Valid(this)

/** 理由を検証の失敗として包む。 */
fun String.asInvalid(): Validation<Nothing> = Invalid(this)

/**
 * 2 つの検証結果を合わせる。
 *
 * 両方成功なら f を適用する。**どちらかでも失敗なら、失敗を全部集める。**
 * これがアプリカティブの合成。
 */
fun <A, B, R> combine(a: Validation<A>, b: Validation<B>, f: (A, B) -> R): Validation<R> =
    when {
        a is Valid && b is Valid -> Valid(f(a.value, b.value))
        else -> Invalid(a.errorsOrEmpty() + b.errorsOrEmpty())
    }

/** 3 つの検証結果を合わせる。2 つ版を 2 回使う。 */
fun <A, B, C, R> combine(
    a: Validation<A>,
    b: Validation<B>,
    c: Validation<C>,
    f: (A, B, C) -> R
): Validation<R> = combine(combine(a, b) { x, y -> x to y }, c) { (x, y), z -> f(x, y, z) }

private fun Validation<*>.errorsOrEmpty(): List<String> =
    when (this) {
        is Valid -> emptyList()
        is Invalid -> errors
    }

/** 検証結果を Outcome に変える。HTTP の層に渡すときに使う。 */
fun <T> Validation<T>.toOutcome(toError: (List<String>) -> ZettaiError): Outcome<ZettaiError, T> =
    when (this) {
        is Valid -> Success(value)
        is Invalid -> Failure(toError(errors))
    }
