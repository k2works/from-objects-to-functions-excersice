package zettai.smoke

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import java.io.File

class DomainBoundaryTest : StringSpec({
    val sources = File("src/main/kotlin/zettai/domain")
        .walkTopDown()
        .filter { it.extension == "kt" }
        .toList()

    "ドメインのソースが存在する" {
        sources.shouldNotBeEmpty()
    }

    "ドメインは HTTP とアダプタへ依存しない" {
        sources.flatMap { file ->
            file.readLines()
                .filter { it.trimStart().startsWith("import ") }
                .filter { "org.http4k" in it || "zettai.web" in it }
                .map { "${file.name}: ${it.trim()}" }
        }.shouldBeEmpty()
    }
})
