package zettai.smoke

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.io.File

class DomainBoundaryTest : StringSpec({
    "domain は application と web と外部 framework に依存しない" {
        val domainSource = File("src/main/kotlin/zettai/domain")
        domainSource.isDirectory shouldBe true
        val sources = domainSource
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        sources.isNotEmpty() shouldBe true
        val forbidden = listOf(
            "zettai.application",
            "zettai.web",
            "org.http4k",
            "kotlinx.coroutines",
            "arrow.",
        )
        val violations = sources.flatMap { source ->
                source.readLines().mapIndexedNotNull { index, line ->
                    forbidden.firstOrNull { line.contains("import $it") }
                        ?.let { "${source.name}:${index + 1}: $line" }
                }
            }
            .toList()

        violations.shouldBeEmpty()
    }
})
