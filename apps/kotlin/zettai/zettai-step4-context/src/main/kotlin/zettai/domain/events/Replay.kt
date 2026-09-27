package zettai.domain.events

/**
 * 出来事の列を状態に適用する。
 *
 * まず再帰で書いて、次に fold で書く。同じことを 2 通りで表している。
 */
fun List<ToDoListEvent>.replayByRecursion(from: ToDoListState): ToDoListState =
    when {
        isEmpty() -> from
        else -> drop(1).replayByRecursion(from.apply(first()))
    }

/** 再帰を fold に置き換えたもの。 */
fun List<ToDoListEvent>.replayFrom(from: ToDoListState): ToDoListState =
    fold(from) { state, event -> state.apply(event) }

/**
 * 状態から状態への変換。
 *
 * 出来事 1 つひとつが、この変換になる。変換どうしは合成でき、
 * 合成しても変換であり続ける。
 */
typealias StateTransition = (ToDoListState) -> ToDoListState

/** 何もしない変換。合成の単位元。 */
val identityTransition: StateTransition = { it }

/** 2 つの変換を繋ぐ。合成の演算。 */
infix fun StateTransition.andThen(next: StateTransition): StateTransition = { next(this(it)) }

/** 出来事を変換として見る。 */
fun ToDoListEvent.asTransition(): StateTransition = { state -> state.apply(this) }

/** 出来事の列を 1 つの変換に畳み込む。 */
fun List<ToDoListEvent>.asTransition(): StateTransition =
    fold(identityTransition) { acc, event -> acc andThen event.asTransition() }
