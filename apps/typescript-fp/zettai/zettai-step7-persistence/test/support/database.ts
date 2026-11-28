import { PostgreSqlContainer } from "@testcontainers/postgresql"
import { Effect } from "effect"
import { Pool } from "pg"
import { createSchema, makePostgresStore } from "../../src/adapter/postgres/event-store.ts"
import type { ToDoListPorts } from "../../src/domain/hub.ts"

export interface TestDatabase {
  readonly pool: Pool
  // 表を空にしてから、保管を返します。
  readonly emptyStore: () => Promise<ToDoListPorts>
  readonly stop: () => Promise<void>
}

export const startDatabase = async (): Promise<TestDatabase> => {
  const container = await new PostgreSqlContainer("postgres:18-alpine").start()
  const pool = new Pool({ connectionString: container.getConnectionUri() })
  await Effect.runPromise(createSchema(pool))

  return {
    pool,
    emptyStore: async () => {
      await pool.query("TRUNCATE todo_list_event RESTART IDENTITY")
      return makePostgresStore(pool)
    },
    stop: async () => {
      await pool.end()
      await container.stop()
    }
  }
}
