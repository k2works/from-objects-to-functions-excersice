package zettai.smoke

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.io.File

class BoundaryTest : StringSpec({
    "domain は application と adapter と HTTP と coroutine runner に依存しない" {
        val sources = productionSources("domain")
        val forbidden = listOf(
            "zettai.application",
            "zettai.adapter",
            "zettai.web",
            "org.http4k",
            "kotlinx.coroutines",
            "java.sql",
            "org.postgresql",
        )

        violations(sources, forbidden).shouldBeEmpty()
    }

    "domain.errors は validation と commands に依存しない（domain の中に循環を作らない）" {
        val sources = productionSources("domain/errors")
        val forbidden = listOf("zettai.domain.validation", "zettai.domain.commands")

        violations(sources, forbidden).shouldBeEmpty()
    }

    "application は driving adapter と driven adapter と coroutine runner に依存しない" {
        val sources = productionSources("application")
        val forbidden = listOf(
            "zettai.adapter",
            "zettai.web",
            "org.http4k",
            "kotlinx.coroutines",
            "java.sql",
            "org.postgresql",
        )

        violations(sources, forbidden).shouldBeEmpty()
    }

    "driving adapter は driven adapter に依存しない" {
        val sources = productionSources("web")
        val forbidden = listOf("zettai.adapter")

        violations(sources, forbidden).shouldBeEmpty()
    }

    "in-memory の adapter は PostgreSQL の adapter と JDBC に依存しない" {
        val sources = productionSources("adapter/inmemory")
        val forbidden = listOf("zettai.adapter.postgres", "java.sql", "org.postgresql")

        violations(sources, forbidden).shouldBeEmpty()
    }

    "PostgreSQL の adapter は in-memory の adapter に依存しない" {
        val sources = productionSources("adapter/postgres")
        val forbidden = listOf("zettai.adapter.inmemory")

        violations(sources, forbidden).shouldBeEmpty()
    }

    "ログの記録の型は、adapter と web と HTTP と JDBC に依存しない（どちらからも使える中立の場所）" {
        val sources = productionSources("logging")
        val forbidden = listOf("zettai.adapter", "zettai.web", "org.http4k", "java.sql", "org.postgresql")

        violations(sources, forbidden).shouldBeEmpty()
    }

    "ログの adapter は、他の adapter と web に依存せず、JDBC も使わない" {
        val sources = productionSources("adapter/logging")
        val forbidden = listOf(
            "zettai.adapter.postgres",
            "zettai.adapter.inmemory",
            "zettai.web",
            "java.sql",
            "org.postgresql",
        )

        sources.isNotEmpty() shouldBe true
        violations(sources, forbidden).shouldBeEmpty()
    }

    "domain と application と adapter に runBlocking が現れない" {
        val sources = listOf("domain", "application", "adapter").flatMap { productionSources(it) }

        sources.filter { it.readText().contains("runBlocking") }.map { it.name }.shouldBeEmpty()
    }

    // adapter は blocking の I/O を Dispatchers.IO に閉じ込めるため、withContext と Dispatchers は使ってよい。
    // 同期と非同期をつなぐ runBlocking は、web の最外周だけに置く。
    "driven adapter は driving adapter と HTTP と runBlocking に依存しない" {
        val sources = productionSources("adapter")
        val forbidden = listOf(
            "zettai.web",
            "org.http4k",
            "kotlinx.coroutines.runBlocking",
        )

        violations(sources, forbidden).shouldBeEmpty()
    }
})

private fun productionSources(packageName: String): List<File> {
    val directory = File("src/main/kotlin/zettai/$packageName")
    directory.isDirectory shouldBe true
    return directory.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()
        .also { it.isNotEmpty() shouldBe true }
}

private fun violations(sources: List<File>, forbidden: List<String>): List<String> =
    sources.flatMap { source ->
        source.readLines().mapIndexedNotNull { index, line ->
            forbidden.firstOrNull { line.contains("import $it") }
                ?.let { "${source.name}:${index + 1}: $line" }
        }
    }
