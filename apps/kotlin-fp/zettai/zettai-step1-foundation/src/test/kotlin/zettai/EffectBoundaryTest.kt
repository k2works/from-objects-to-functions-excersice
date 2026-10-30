package zettai

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status

class EffectBoundaryTest : FunSpec({
    test("同期 HTTP 境界で suspend 処理を一度だけ実行する") {
        var calls = 0
        val response = effectfulHandler {
            calls += 1
            "準備完了"
        }(Request(GET, "/health"))

        response.status shouldBe Status.OK
        response.bodyString() shouldBe "準備完了"
        calls shouldBe 1
    }
})
