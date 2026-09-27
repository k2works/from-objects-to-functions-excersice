package zettai.ui

import zettai.fp.Failure
import zettai.fp.Outcome
import zettai.fp.Success

/** テンプレートの適用に失敗した理由。 */
data class TemplateError(val message: String)

/** テンプレートに差し込む値。 */
sealed interface TemplateTag

data class StringTag(val text: String) : TemplateTag

data class ListTag(val rows: List<Map<String, TemplateTag>>) : TemplateTag

data class BooleanTag(val value: Boolean) : TemplateTag

/**
 * テンプレート。
 *
 * 既製のテンプレートエンジンを使っていない。狙いは「テンプレートの書き方」ではなく、
 * **未適用のタグが残っていたら失敗にする**ことにある。
 * タグを書いたのにデータを渡し忘れたら、画面が壊れる前に気づける。
 */
data class Template(val text: String) {
    /** タグを適用する。未適用のタグが残っていたら失敗。 */
    fun render(data: Map<String, TemplateTag>): Outcome<TemplateError, String> =
        applyTags(text, data).checkNoTagsLeft()
}

private val TAG = Regex("""\{\{(\w+)}}""")
private val SECTION = Regex("""\{\{#(\w+)}}(.*?)\{\{/\1}}""", RegexOption.DOT_MATCHES_ALL)

private fun applyTags(text: String, data: Map<String, TemplateTag>): String =
    applySections(text, data).let { applyValues(it, data) }

private fun applySections(text: String, data: Map<String, TemplateTag>): String =
    SECTION.replace(text) { match ->
        val (name, body) = match.destructured

        when (val tag = data[name]) {
            is ListTag -> tag.rows.joinToString("") { row -> applyValues(body, row) }
            is BooleanTag -> if (tag.value) applyValues(body, data) else ""
            else -> match.value
        }
    }

private fun applyValues(text: String, data: Map<String, TemplateTag>): String =
    TAG.replace(text) { match ->
        when (val tag = data[match.groupValues[1]]) {
            is StringTag -> tag.text
            else -> match.value
        }
    }

private fun String.checkNoTagsLeft(): Outcome<TemplateError, String> {
    val left = TAG.findAll(this).map { it.groupValues[1] }.toList()

    return if (left.isEmpty()) {
        Success(this)
    } else {
        Failure(TemplateError("適用されていないタグが残っています: ${left.joinToString(", ")}"))
    }
}
