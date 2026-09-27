package zettai.smoke

import java.io.File
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEmpty
import strikt.assertions.isNotEmpty

/**
 * ドメインとアダプタの境界を検査する。
 *
 * 境界の定義は「zettai.domain のファイルはフレームワークを import しない」。
 * コメントや命名規約と違い、import は書けば残るので機械的に検査できる。
 */
class DomainBoundaryTest {

    private val frameworks = listOf("org.http4k", "java.sql", "org.jetbrains.exposed", "com.ubertob.kondor")

    /**
     * アダプタのパッケージ。ドメインはこれらを知らない。
     *
     * フレームワークだけを見ていると「ドメインが自前のアダプタに依存する」
     * 逆流を見逃す。第 12 章の主題（ドメインはログを知らない）はここで守る。
     */
    private val adapters = listOf("zettai.web", "zettai.persistence", "zettai.logger", "zettai.json", "zettai.ui")

    /** ドメインと関数型の部品。どちらもフレームワークを知らない。 */
    private val domainSources = listOf("src/main/kotlin/zettai/domain", "src/main/kotlin/zettai/fp")
        .map(::File)
        .filter { it.exists() }
        .flatMap { it.walkTopDown() }
        .filter { it.extension == "kt" }

    /**
     * 検査対象が 0 件でも「違反 0 件」になってしまう穴を塞ぐ。
     * 番人が黙って通す状態を、番人自身のテストで検出する。
     */
    @Test
    fun `検査対象のファイルが存在する`() {
        expectThat(domainSources).isNotEmpty()
    }

    @Test
    fun `ドメインと fp はフレームワークを import しない`() {
        val violations = domainSources.flatMap { file ->
            file.readLines()
                .filter { it.trimStart().startsWith("import ") }
                .filter { line -> (frameworks + adapters).any { line.contains(it) } }
                .map { "${file.name}: ${it.trim()}" }
        }.toList()

        expectThat(violations).isEmpty()
    }
}
