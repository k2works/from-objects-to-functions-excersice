package zettai.smoke

import java.io.File
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue

/**
 * Unit 1 のデモ項目を自動化したスモークテスト。
 *
 * 第 1 章で読者に示した「手元で再現できる」状態が壊れていないことを確かめる。
 * 手で実演していた項目を機械が確かめる形に移した。
 */
class BuildEnvironmentSmokeTest {

    // テストの作業ディレクトリはモジュール（apps/kotlin/zettai/zettai-step1-http）
    private val repositoryRoot = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "flake.nix").exists() }

    @Test
    fun `JDK 21 で動いている`() {
        expectThat(Runtime.version().feature()).isEqualTo(21)
    }

    @Test
    fun `kotlin devShell が flake に登録されている`() {
        val flake = File(repositoryRoot, "flake.nix")

        expectThat(flake.exists()).isTrue()
        expectThat(flake.readText()).contains("kotlin = import ./ops/nix/environments/kotlin/shell.nix")
    }

    @Test
    fun `devShell の定義が存在する`() {
        expectThat(File(repositoryRoot, "ops/nix/environments/kotlin/shell.nix").exists()).isTrue()
    }

    @Test
    fun `第 1 章が公開され nav から辿れる`() {
        expectThat(File(repositoryRoot, "docs/article/zettai/kotlin/chapter01.md").exists()).isTrue()
        expectThat(File(repositoryRoot, "mkdocs.yml").readText())
            .contains("article/zettai/kotlin/chapter01.md")
    }
}
