import { Cause, Effect, Exit, Result } from "effect"
import { Pool } from "pg"
import type { PoolClient } from "pg"
import type { ToDoListEvent } from "../../domain/events.ts"
import type { Decide, ToDoListPorts } from "../../domain/hub.ts"
import { belongsTo } from "../../domain/streams.ts"
import type { StreamId } from "../../domain/streams.ts"
import { decodeEvent, encodePayload, entityId, userPrefix } from "./codec.ts"

interface EventRow {
  readonly event_type: string
  readonly payload: unknown
}

// 出来事は追記だけします。UPDATE も DELETE もしません。順序の根拠は id です。
const CREATE_TABLE = `
  CREATE TABLE IF NOT EXISTS todo_list_event (
    id          BIGSERIAL PRIMARY KEY,
    entity_id   TEXT        NOT NULL,
    event_type  TEXT        NOT NULL,
    payload     JSONB       NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
  )`

const CREATE_INDEX = `
  CREATE INDEX IF NOT EXISTS idx_todo_list_event_entity ON todo_list_event (entity_id, id)`

const INSERT = `
  INSERT INTO todo_list_event (entity_id, event_type, payload) VALUES ($1, $2, $3)`

// 同じストリームの更新を、トランザクションの終わりまで 1 つずつにします。まだ行が無くても効きます。
const LOCK_STREAM = "SELECT pg_advisory_xact_lock(hashtextextended($1, 0))"

const SELECT_BY_ENTITIES = `
  SELECT event_type, payload FROM todo_list_event WHERE entity_id = ANY($1) ORDER BY id`

const SELECT_BY_ENTITY = `
  SELECT event_type, payload FROM todo_list_event WHERE entity_id = $1 ORDER BY id`

// LIKE は使いません。置き換えた名前に含まれる % が、ワイルドカードになるためです。
const SELECT_BY_USER = `
  SELECT event_type, payload FROM todo_list_event WHERE starts_with(entity_id, $1) ORDER BY id`

// Promise の失敗は、defect になります。データベースの失敗は、想定外の失敗として扱います。
const query = (target: Pool | PoolClient, text: string, values: ReadonlyArray<unknown> = []) =>
  Effect.promise(() => target.query<EventRow>(text, [...values]))

// 借りた接続は、成功、失敗、中断のどれでも返します。
// defect と中断では、接続の状態が分からないので捨てます。業務の失敗では、返して使い続けます。
const isBroken = (exit: Exit.Exit<unknown, unknown>): boolean =>
  Exit.isFailure(exit) && (Cause.hasDies(exit.cause) || Cause.hasInterrupts(exit.cause))

const withClient = <A, E>(pool: Pool, use: (client: PoolClient) => Effect.Effect<A, E>) =>
  Effect.acquireUseRelease(
    Effect.promise(() => pool.connect()),
    use,
    (client, exit) => Effect.sync(() => client.release(isBroken(exit)))
  )

const transaction = <A, E>(client: PoolClient, body: Effect.Effect<A, E>) =>
  Effect.acquireUseRelease(
    query(client, "BEGIN"),
    () => body,
    // 本体が成功した場合だけ確定します。失敗と中断は、取り消します。
    (_begin, exit) => Effect.asVoid(query(client, Exit.isSuccess(exit) ? "COMMIT" : "ROLLBACK"))
  )

const insert = (client: PoolClient, event: ToDoListEvent) =>
  query(client, INSERT, [
    entityId(event.user, event.listName),
    event._tag,
    JSON.stringify(encodePayload(event))
  ])

// 読めない行は、黙って捨てずに defect にします。
const decodeRow = ({ event_type, payload }: EventRow): Effect.Effect<ToDoListEvent> =>
  Result.match(decodeEvent(event_type, payload), {
    onFailure: (cause) => Effect.die(new Error("保存された行を読めません", { cause })),
    onSuccess: (event) => Effect.succeed(event)
  })

const select = (target: Pool | PoolClient, text: string, key: unknown) =>
  query(target, text, [key]).pipe(
    Effect.flatMap((result) => Effect.forEach(result.rows, decodeRow))
  )

// ロックは、いつも同じ順に取ります。順が違うと、互いに相手を待って行き詰まります。
const lockStreams = (client: PoolClient, entities: ReadonlyArray<string>) =>
  Effect.forEach(entities.toSorted(), (entity) => query(client, LOCK_STREAM, [entity]), {
    discard: true
  })

// ロック、読み取り、判断、追記を、1 つのトランザクションの中で順に実行します。
const updateIn = <E>(client: PoolClient, streams: ReadonlyArray<StreamId>, decide: Decide<E>) => {
  const entities = streams.map((stream) => entityId(stream.user, stream.listName))
  return Effect.gen(function* () {
    yield* lockStreams(client, entities)
    const history = yield* select(client, SELECT_BY_ENTITIES, entities)
    const events = yield* Effect.suspend(() => Effect.fromResult(decide(history)))
    if (!events.every((event) => belongsTo(event, streams))) {
      return yield* Effect.die(new Error("対象でないストリームへの追記です"))
    }
    yield* Effect.forEach(events, (event) => insert(client, event), { discard: true })
    return events
  })
}

export const createSchema = (pool: Pool): Effect.Effect<void> =>
  query(pool, CREATE_TABLE).pipe(Effect.andThen(query(pool, CREATE_INDEX)), Effect.asVoid)

export const makePostgresStore = (pool: Pool): ToDoListPorts => ({
  readListEvents: (user, listName) => select(pool, SELECT_BY_ENTITY, entityId(user, listName)),
  readUserEvents: (user) => select(pool, SELECT_BY_USER, userPrefix(user)),
  // 1 回の更新は、1 つのトランザクションです。
  update: (streams, decide) =>
    withClient(pool, (client) => transaction(client, updateIn(client, streams, decide)))
})

// 接続の集まりを、Scope の寿命にします。スコープを抜けるときに、すべて閉じます。
export const postgresPool = (connectionString: string) =>
  Effect.acquireRelease(
    Effect.sync(() => {
      const pool = new Pool({ connectionString })
      // 使っていない接続の切断は、ここに届きます。受け取らないと、プロセスが終了します。
      pool.on("error", () => {})
      return pool
    }),
    (pool) => Effect.promise(() => pool.end())
  )
