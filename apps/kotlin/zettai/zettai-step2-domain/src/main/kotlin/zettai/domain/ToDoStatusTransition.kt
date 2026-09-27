package zettai.domain

/**
 * 許される状態遷移。
 *
 * 遷移表をコードにしたもの。ここに無い遷移は許さない。
 * 表を先に作ってからコードにすると、「許さない遷移」を書き漏らさない。
 */
private val allowed: Map<ToDoStatus, Set<ToDoStatus>> = mapOf(
    ToDoStatus.Todo to setOf(ToDoStatus.InProgress, ToDoStatus.Done),
    ToDoStatus.InProgress to setOf(ToDoStatus.Done, ToDoStatus.Blocked),
    ToDoStatus.Blocked to setOf(ToDoStatus.InProgress),
    ToDoStatus.Done to emptySet()
)

/** この状態から次の状態へ遷移できるか。同じ状態への遷移は変化が無いので許さない。 */
fun ToDoStatus.canTransitionTo(next: ToDoStatus): Boolean = next in allowed.getValue(this)
