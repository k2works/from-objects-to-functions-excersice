package zettai.domain

import zettai.fp.Validation
import zettai.fp.asInvalid
import zettai.fp.asValid
import zettai.fp.combine

/**
 * リスト名の検証。
 *
 * 2 つの条件を独立に確かめ、**両方だめなら両方の理由を返す**。
 * Outcome（モナド）で書くと 1 つめがだめな時点で止まり、2 つめは確かめられない。
 */
private const val MAX_LIST_NAME_LENGTH = 40

fun validateListName(raw: String): Validation<ListName> =
    combine(notBlank(raw), withinLength(raw)) { _, value -> ListName(value) }

private fun notBlank(raw: String): Validation<String> =
    if (raw.isNotBlank()) raw.asValid() else "リスト名を入力してください".asInvalid()

private fun withinLength(raw: String): Validation<String> =
    if (raw.length <= MAX_LIST_NAME_LENGTH) {
        raw.asValid()
    } else {
        "リスト名は $MAX_LIST_NAME_LENGTH 文字以内にしてください".asInvalid()
    }
