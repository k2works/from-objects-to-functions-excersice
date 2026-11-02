package zettai

import org.testcontainers.postgresql.PostgreSQLContainer

/** 結合テスト用の PostgreSQL。最初に使われたときに 1 度だけ Testcontainers で起動し、JVM が終わると片付く。 */
object TestDatabase:

  private lazy val container: PostgreSQLContainer =
    val started = new PostgreSQLContainer("postgres:17-alpine")
    started.start()
    started

  lazy val config: PostgresStore.Config =
    PostgresStore.Config(container.getJdbcUrl, container.getUsername, container.getPassword)
