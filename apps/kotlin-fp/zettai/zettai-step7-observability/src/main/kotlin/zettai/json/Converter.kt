package zettai.json

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** 変換の失敗。理由には入力の値を含めない（入力に機密があっても、理由から漏れない）。 */
data class ConversionFailure(val reason: String)

/**
 * A を B にする純粋な関数。入力の側に関数を前置し（反変）、出力の側に関数を後置する（共変）ので、
 * 2 つを一度に行う [dimap] を持つプロファンクタの形をしている。
 */
data class Encoder<A, B>(val run: (A) -> B) {
    fun <C, D> dimap(contramap: (C) -> A, map: (B) -> D): Encoder<C, D> =
        Encoder { map(run(contramap(it))) }
}

/** B を A に読む純粋な関数。読めないときは例外ではなく [ConversionFailure] の `Left`。[Encoder] と同じ形の [dimap] を持つ。 */
data class Decoder<B, A>(val run: (B) -> Either<ConversionFailure, A>) {
    fun <C, D> dimap(contramap: (C) -> B, map: (A) -> D): Decoder<C, D> =
        Decoder { run(contramap(it)).map(map) }
}

/**
 * 双方向の変換。往復（`decoder.run(encoder.run(a)) == a.right()`）が契約で、性質テストで確かめる。
 * [dimap] が使えるのは [Encoder] と [Decoder] それぞれで、2 つを束ねた [Converter] には、
 * 入力と出力を同時に写す一般の `dimap` は無い（行きと帰りで逆向きの関数が要るため）。繋げるのは [andThen]。
 */
data class Converter<A, B>(val encoder: Encoder<A, B>, val decoder: Decoder<B, A>) {
    fun <C> andThen(next: Converter<B, C>): Converter<A, C> =
        Converter(
            encoder = encoder.dimap({ it }, next.encoder.run),
            decoder = Decoder { c -> next.decoder.run(c).flatMap(decoder.run) },
        )
}

/** JSON の木と、1 行の文字列。escape は kotlinx-serialization-json に任せる（ADR-044）。 */
val jsonText: Converter<JsonElement, String> =
    Converter(
        encoder = Encoder { Json.encodeToString(JsonElement.serializer(), it) },
        decoder = Decoder { text ->
            Either.catch { Json.parseToJsonElement(text) }.fold(
                ifLeft = { ConversionFailure("JSON として読めない").left() },
                ifRight = { it.right() },
            )
        },
    )
