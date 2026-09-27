package zettai.fp

/**
 * 双方向の変換。
 *
 * 書き出し（render）と読み込み（parse）を 1 つの型で対にする。
 * 片方だけ直して壊れることを防ぐ。
 *
 * 出力側は map で、入力側は contramap で変換できる。
 * 両方持つ構造をプロファンクタと呼ぶ。
 */
data class Converter<A, B>(
    val render: (A) -> B,
    val parse: (B) -> Outcome<ZettaiError, A>
) {
    /** 出力側（B）を変換する。ファンクタの map と同じ向き。 */
    fun <C> map(to: (B) -> C, from: (C) -> B): Converter<A, C> =
        Converter(
            render = { to(render(it)) },
            parse = { parse(from(it)) }
        )

    /** 入力側（A）を変換する。向きが逆なので contramap。 */
    fun <C> contramap(to: (C) -> A, from: (A) -> C): Converter<C, B> =
        Converter(
            render = { render(to(it)) },
            parse = { parse(it).map(from) }
        )

    /** 書き出して読み込む。往復できることを確かめるのに使う。 */
    fun roundTrip(value: A): Outcome<ZettaiError, A> = parse(render(value))
}

/** 何も変換しない。プロファンクタの単位元にあたる。 */
fun <A> identityConverter(): Converter<A, A> = Converter(render = { it }, parse = { Success(it) })
