package zettai.fp

/**
 * 文脈を受け取ってから値を返す計算。
 *
 * データベースアクセスは「接続がある状態で」しか実行できない。
 * その「接続がある状態」を文脈として引数に取り、実行を後回しにする。
 *
 * map で繋ぐとファンクタ、flatMap で繋ぐとモナドになる。
 * flatMap があると「前の結果を使って次を決める」計算を繋げられる。
 */
fun interface ContextReader<CTX, out T> {
    fun runWith(context: CTX): T

    fun <U> map(f: (T) -> U): ContextReader<CTX, U> = ContextReader { context -> f(runWith(context)) }

    /** 前の結果を使って次の計算を決める。これがあるとモナド。 */
    fun <U> flatMap(f: (T) -> ContextReader<CTX, U>): ContextReader<CTX, U> =
        ContextReader { context -> f(runWith(context)).runWith(context) }
}

/** 文脈を使わずに値を返す。モナドの単位元にあたる。 */
fun <CTX, T> pure(value: T): ContextReader<CTX, T> = ContextReader { value }
