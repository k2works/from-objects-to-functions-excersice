import { PostgreSqlContainer } from "@testcontainers/postgresql"
import { expect, it } from "vitest"

// Testcontainers で PostgreSQL を起動できることを確かめます。DB のドライバは、第 9 章で選びます。
// Docker が無い場合は、skip せずに失敗します。
it("starts PostgreSQL and answers a query", { timeout: 120_000 }, async () => {
  const container = await new PostgreSqlContainer("postgres:18-alpine").start()
  try {
    const result = await container.exec([
      "psql",
      "-U",
      container.getUsername(),
      "-d",
      container.getDatabase(),
      "-tAc",
      "select 1"
    ])
    expect([result.exitCode, result.output.trim()]).toEqual([0, "1"])
  } finally {
    await container.stop()
  }
})
