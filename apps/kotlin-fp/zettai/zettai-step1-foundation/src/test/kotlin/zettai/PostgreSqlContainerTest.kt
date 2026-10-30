package zettai

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.sql.DriverManager
import org.testcontainers.postgresql.PostgreSQLContainer

class PostgreSqlContainerTest : FunSpec({
    test("container が公開した接続情報だけで PostgreSQL に接続できる") {
        PostgreSQLContainer("postgres:18-alpine").use { postgres ->
            postgres.start()
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("select 1").use { result ->
                        result.next() shouldBe true
                        result.getInt(1) shouldBe 1
                    }
                }
            }
        }
    }
})
