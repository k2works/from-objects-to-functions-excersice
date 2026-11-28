import { Cause, Effect, Exit } from "effect"
import type { ToDoListPorts } from "../../domain/hub.ts"
import type { StreamId } from "../../domain/streams.ts"
import type { ListName, User } from "../../domain/todo-list.ts"
import type { App } from "../../web/app.ts"

// 出力先と時刻は、引数で受け取ります。
export interface Logging {
  readonly write: (line: string) => void
  readonly now: () => number
}

interface Finished {
  // "success"、業務の失敗の種類、"defect:クラス名"、"interrupted" のどれかです。
  readonly outcome: string
  readonly durationMs: number
}

// ログの記録の形です。ただし、型は余分な項目を止めません。書き出すときに、項目名で絞ります。
export type LogRecord = Finished &
  (
    | {
        readonly operation: "update"
        readonly streams: ReadonlyArray<StreamId>
        readonly eventCount?: number
      }
    | {
        readonly operation: "readListEvents"
        readonly user: User
        readonly listName: ListName
        readonly eventCount?: number
      }
    | { readonly operation: "readUserEvents"; readonly user: User; readonly eventCount?: number }
    | {
        readonly operation: "request"
        readonly method: string
        readonly path: string
        readonly status?: number
      }
  )

const tagOf = (error: unknown): string =>
  typeof error === "object" && error !== null && "_tag" in error ? String(error._tag) : "failure"

// 例外は、クラス名だけを残します。メッセージとスタックトレースには、機密が混ざりえます。
const classOf = (defect: unknown): string =>
  defect instanceof Error ? defect.constructor.name : typeof defect

const outcomeOf = (exit: Exit.Exit<unknown, unknown>): string => {
  if (Exit.isSuccess(exit)) return "success"
  const die = exit.cause.reasons.find(Cause.isDieReason)
  if (die !== undefined) return `defect:${classOf(die.defect)}`
  const fail = exit.cause.reasons.find(Cause.isFailReason)
  return fail === undefined ? "interrupted" : tagOf(fail.error)
}

// 書き出してよい項目の名前です。入れ子の中も、この名前の項目だけが出ます。
const FIELDS = [
  "operation",
  "outcome",
  "durationMs",
  "streams",
  "user",
  "listName",
  "eventCount",
  "method",
  "path",
  "status"
]

export const toLine = (record: LogRecord): string => JSON.stringify(record, FIELDS)

// 出力先の失敗で、本体の結果を変えないようにします。そのログの行は失われます。
const emit = (logging: Logging, record: LogRecord) =>
  Effect.sync(() => {
    logging.write(toLine(record))
  }).pipe(Effect.catchCause(() => Effect.void))

// 本体がどう終わっても、1 行を書きます。本体の結果は変えません。
const observed = <A, E, R>(
  logging: Logging,
  body: Effect.Effect<A, E, R>,
  record: (finished: Finished, exit: Exit.Exit<A, E>) => LogRecord
): Effect.Effect<A, E, R> =>
  Effect.suspend(() => {
    const started = logging.now()
    return body.pipe(
      Effect.onExit((exit) => {
        const finished = { outcome: outcomeOf(exit), durationMs: logging.now() - started }
        return emit(logging, record(finished, exit))
      })
    )
  })

// 出来事の件数は、成功した場合だけ載せます。中身は載せません。
const countOf = (exit: Exit.Exit<ReadonlyArray<unknown>, unknown>) =>
  Exit.isSuccess(exit) ? { eventCount: exit.value.length } : {}

// 保管の 3 つの操作を包みます。ハブと domain は、ログを知りません。
export const loggedPorts = (ports: ToDoListPorts, logging: Logging): ToDoListPorts => ({
  readListEvents: (user, listName) =>
    observed(logging, ports.readListEvents(user, listName), (finished, exit) => ({
      operation: "readListEvents",
      user,
      listName,
      ...countOf(exit),
      ...finished
    })),
  readUserEvents: (user) =>
    observed(logging, ports.readUserEvents(user), (finished, exit) => ({
      operation: "readUserEvents",
      user,
      ...countOf(exit),
      ...finished
    })),
  update: (streams, decide) =>
    observed(logging, ports.update(streams, decide), (finished, exit) => ({
      operation: "update",
      streams,
      ...countOf(exit),
      ...finished
    }))
})

// 要求は、メソッドとパスと状態だけを残します。クエリと本文は載せません。
export const loggedApp =
  (app: App, logging: Logging): App =>
  (request) =>
    observed(logging, app(request), (finished, exit) => ({
      operation: "request",
      method: request.method,
      path: new URL(request.url).pathname,
      ...(Exit.isSuccess(exit) ? { status: exit.value.status } : {}),
      ...finished
    }))
