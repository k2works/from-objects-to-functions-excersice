package zettai.json

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * `Encoder`（A → B）と `Decoder`（B → Either<失敗, A>）は、どちらもプロファンクタの形をしている。
 * 入力の側に関数を前置し（反変）、出力の側に関数を後置する（共変）。`dimap` はその両方を一度に行う。
 * 法則は Haskell の `Profunctor` と同じ：
 *   dimap id id = id
 *   dimap (f ∘ g) (h ∘ k) = dimap g h ∘ dimap f k
 */
class ConverterLawsTest : StringSpec({
    // 制御文字、行区切り（U+2028）、絵文字を含む、サロゲートを除くすべてのコードポイント
    val anyCodepoint: Arb<Codepoint> = Arb.int(0..0x10FFFF).filter { it !in 0xD800..0xDFFF }.map { Codepoint(it) }
    // 傾きと切片を変えた一次関数の族。順序を取り違えると、結果が変わる
    val linear: Arb<(Int) -> Int> = Arb.bind(Arb.int(-5..5), Arb.int(-5..5)) { a, b -> { n: Int -> n * a + b } }
    val plus = Encoder<Int, Int> { it + 1 }
    val decode = Decoder<Int, Int> { if (it >= 0) it.right() else ConversionFailure("負の数").left() }

    "Encoder の dimap は恒等則を満たす" {
        checkAll(Arb.int(-1000..1000)) { x ->
            plus.dimap({ a: Int -> a }, { b: Int -> b }).run(x) shouldBe plus.run(x)
        }
    }

    "Encoder の dimap は合成則を満たす" {
        checkAll(Arb.int(-1000..1000), linear, linear, linear, linear) { x, f, g, h, k ->
            val left = plus.dimap({ n: Int -> f(g(n)) }, { n: Int -> h(k(n)) })
            val right = plus.dimap(f, k).dimap(g, h)

            left.run(x) shouldBe right.run(x)
        }
    }

    "Decoder の dimap は恒等則を満たす" {
        checkAll(Arb.int(-1000..1000)) { x ->
            decode.dimap({ a: Int -> a }, { b: Int -> b }).run(x) shouldBe decode.run(x)
        }
    }

    "Decoder の dimap は合成則を満たす" {
        checkAll(Arb.int(-1000..1000), linear, linear, linear, linear) { x, f, g, h, k ->
            val left = decode.dimap({ n: Int -> f(g(n)) }, { n: Int -> h(k(n)) })
            val right = decode.dimap(f, k).dimap(g, h)

            left.run(x) shouldBe right.run(x)
        }
    }

    "Decoder の失敗は dimap で変わらない" {
        decode.dimap({ n: Int -> n - 100 }, { n: Int -> n + 1 }).run(5) shouldBe
            ConversionFailure("負の数").left()
    }

    "JSON の木と文字列の Converter は、任意の文字列を含む木を往復する" {
        checkAll(Arb.string(0..40, anyCodepoint)) { text ->
            val tree = JsonObject(mapOf("message" to JsonPrimitive(text)))

            jsonText.decoder.run(jsonText.encoder.run(tree)) shouldBe tree.right()
        }
    }

    "JSON の文字列にした木は、改行を含まない 1 行になる" {
        checkAll(Arb.string(0..40, anyCodepoint)) { text ->
            val line = jsonText.encoder.run(JsonObject(mapOf("message" to JsonPrimitive(text + "\n" + text))))

            line.contains('\n') shouldBe false
        }
    }

    "壊れた JSON は例外ではなく Left になり、失敗の理由に入力を含めない" {
        val broken = "{\"password\": \"secret\", "

        val result = jsonText.decoder.run(broken)

        val failure = result.leftOrNull().shouldBeInstanceOf<ConversionFailure>()
        failure.reason.contains("secret") shouldBe false
    }

    "andThen で繋いだ Converter も往復する" {
        val numberToTree = Converter<Int, JsonElement>(
            Encoder { JsonPrimitive(it) },
            Decoder { element ->
                (element as? JsonPrimitive)?.content?.toIntOrNull()?.right()
                    ?: ConversionFailure("整数ではない").left()
            },
        )
        val numberToText = numberToTree.andThen(jsonText)

        checkAll(Arb.int()) { n ->
            numberToText.decoder.run(numberToText.encoder.run(n)) shouldBe n.right()
        }
    }
})
