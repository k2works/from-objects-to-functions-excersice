package zettai.app

import java.time.Instant
import org.http4k.core.HttpHandler
import org.http4k.core.then
import zettai.adapter.logging.logged
import zettai.application.EventStore
import zettai.application.todoListHub
import zettai.web.requestLogged
import zettai.web.zettaiHttpApp

/**
 * ログを足した app の組み立て箇所。store と Hub を decorator で包み、request の filter を最も外に付ける。
 * `zettaiHttpApp` の `CatchAll` より外にあるので、予期しない例外は 500 の request の行として残る。
 * 出力先 [log] は、投げない契約とする（投げると、本体の結果が失われる）。
 */
fun <Tx> observedApp(store: EventStore<Tx>, log: (String) -> Unit, now: () -> Instant): HttpHandler {
    val hub = todoListHub(store.logged(log, now)).logged(log, now)
    return requestLogged(log, now).then(zettaiHttpApp(hub))
}
