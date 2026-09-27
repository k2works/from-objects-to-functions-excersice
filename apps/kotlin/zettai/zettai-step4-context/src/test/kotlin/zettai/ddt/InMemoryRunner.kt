package zettai.ddt

import zettai.domain.HubAction
import zettai.domain.InMemoryContext

/**
 * 接続を持たない文脈で HubAction を実行する。
 *
 * インメモリの経路は接続を必要としない。文脈を型（TxContext）にしたので、
 * 「接続を持たない文脈」を渡せる。永続化の操作を混ぜたら require で失敗する。
 */
fun <T> runWithoutContext(action: HubAction<T>): T = action.runWith(InMemoryContext)
