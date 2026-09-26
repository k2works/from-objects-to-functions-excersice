package zettai

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

/**
 * ビルド基盤が動くことを確認するスモークテスト。
 *
 * Kotlin コンパイラ・JUnit 5・Strikt の 3 つが組み合わさって動くことだけを見る。
 * 業務的な意味は無いので、第 2 章で縦串が通ったら削除する。
 */
class BuildSmokeTest {

    @Test
    fun `アプリケーション名を返す`() {
        expectThat(applicationName()).isEqualTo("Zettai")
    }
}
