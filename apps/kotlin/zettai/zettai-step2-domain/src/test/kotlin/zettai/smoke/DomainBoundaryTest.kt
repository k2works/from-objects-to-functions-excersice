package zettai.smoke

import java.io.File
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEmpty

/**
 * ドメインとアダプタの境界を検査する。
 *
 * 境界の定義は「zettai.domain のファイルはフレームワークを import しない」。
 * コメントや命名規約と違い、import は書けば残るので機械的に検査できる。
 */
class DomainBoundaryTest {

    private val frameworks = listOf("org.http4k", "java.sql", "org.jetbrains.exposed", "com.ubertob.kondor")

    private val domainSources = File("src/main/kotlin/zettai/domain")
        .walkTopDown()
        .filter { it.extension == "kt" }

    @Test
    fun `ドメインはフレームワークを import しない`() {
        val violations = domainSources.flatMap { file ->
            file.readLines()
                .filter { it.trimStart().startsWith("import ") }
                .filter { line -> frameworks.any { line.contains(it) } }
                .map { "${file.name}: ${it.trim()}" }
        }.toList()

        expectThat(violations).isEmpty()
    }
}
