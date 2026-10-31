package zettai.web

import arrow.core.Either
import arrow.core.left
import arrow.core.right

/** 値を埋められなかったタグの名前。出現順で、重複しない。 */
data class UnappliedTags(val names: List<String>)

private val TAG = """\{\{\{(\w+)\}\}\}|\{\{(\w+)\}\}""".toRegex()

/**
 * `{{x}}` は HTML エスケープして、`{{{x}}}` は生のまま埋める。
 * 埋める値は一度の走査で差し込み、差し込んだ値の中のタグはもう一度は解釈しない。
 * 値の無いタグが 1 つでもあれば、画面に `{{x}}` を残さず、失敗にする。
 */
fun renderTemplate(template: String, values: Map<String, String>): Either<UnappliedTags, String> {
    val unapplied = linkedSetOf<String>()
    val rendered = TAG.replace(template) { match ->
        val raw = match.groups[1]?.value
        val name = raw ?: match.groupValues[2]
        val value = values[name]
        when {
            value == null -> {
                unapplied += name
                match.value
            }

            raw != null -> value
            else -> escapeHtml(value)
        }
    }
    return if (unapplied.isEmpty()) rendered.right() else UnappliedTags(unapplied.toList()).left()
}
