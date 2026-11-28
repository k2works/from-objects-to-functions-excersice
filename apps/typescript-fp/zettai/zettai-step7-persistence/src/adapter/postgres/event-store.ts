import { Effect, Exit, Result } from "effect"
import { Pool } from "pg"
import type { PoolClient } from "pg"
import type { ToDoListEvent } from "../../domain/events.ts"
import type { ToDoListPorts } from "../../domain/hub.ts"
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

const SELECT_BY_ENTITY = `
  SELECT event_type, payload FROM todo_list_event WHERE entity_id = $1 ORDER BY id`

// LIKE は使いません。置き換えた名前に含まれる % が、ワイルドカードになるためです。
const SELECT_BY_USER = `
  SELECT event_type, payload FROM todo_list_event WHERE starts_with(entity_id, $1) ORDER BY id`

// Promise の失敗は、defect になります。データベースの失敗は、想定外の失敗として扱います。
const query = (target: Pool | PoolClient, text: string, values: ReadonlyArray<unknown> = []) =>
  Effect.promise(() => target.query<EventRow>(text, [...values]))

// 借りた接続は、成功、失敗、中断のどれでも返します。失敗した接続は、状態が分からないので捨てます。
const withClient = <A, E>(pool: Pool, use: (client: PoolClient) => Effect.Effect<A, E>) =>
  Effect.acquireUseRelease(
    Effect.promise(() => pool.connect()),
    use,
    (client, exit) => Effect.sync(() => client.release(Exit.isFailure(exit)))
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

const select = (pool: Pool, text: string, key: string) =>
  query(pool, text, [key]).pipe(Effect.flatMap((result) => Effect.forEach(result.rows, decodeRow)))

export const createSchema = (pool: Pool): Effect.Effect<void> =>
  query(pool, CREATE_TABLE).pipe(Effect.andThen(query(pool, CREATE_INDEX)), Effect.asVoid)

export const makePostgresStore = (pool: Pool): ToDoListPorts => ({
  readListEvents: (user, listName) => select(pool, SELECT_BY_ENTITY, entityId(user, listName)),
  readUserEvents: (user) => select(pool, SELECT_BY_USER, userPrefix(user)),
  // 1 回の保存は、1 つのトランザクションです。
  persist: (events) =>
    withClient(pool, (client) =>
      transaction(
        client,
        Effect.forEach(events, (event) => insert(client, event), { discard: true })
      )
    )
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
