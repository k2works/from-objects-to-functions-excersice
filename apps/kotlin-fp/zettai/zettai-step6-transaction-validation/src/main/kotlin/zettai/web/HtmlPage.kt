package zettai.web

import java.net.URLEncoder
import zettai.domain.ListName
import zettai.domain.ToDoList
import zettai.domain.User
import zettai.domain.validation.FieldProblem
import zettai.domain.validation.InputProblem

private fun template(name: String): String =
    requireNotNull(object {}.javaClass.getResource("/templates/$name")) { "テンプレートが無い: $name" }.readText()

/** 値の無いタグは描画の失敗にする。`CatchAll` が 500 にし、`{{x}}` が画面に残ることはない。 */
private fun render(name: String, values: Map<String, String>): String =
    renderTemplate(template(name), values).fold(
        ifLeft = { error("テンプレート $name に値が無いタグ: ${it.names}") },
        ifRight = { it },
    )

internal fun encodePathSegment(value: String): String = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

internal fun listPath(user: User, listName: ListName): String =
    "/todo/${encodePathSegment(user.name)}/${encodePathSegment(listName.name)}"

fun renderHtml(user: User, list: ToDoList): String =
    render(
        "list.html",
        mapOf(
            "title" to list.listName.name,
            "items" to list.items.joinToString("") { "<li>${escapeHtml(it.description)}</li>\n" },
            "renameHref" to "${listPath(user, list.listName)}/rename",
        ),
    )

/** 検証の誤りは、すべて並べる。入力は保持して、書き直さなくて済むようにする。 */
fun renderRenameForm(user: User, listName: ListName, newName: String, problems: List<FieldProblem>): String =
    render(
        "rename.html",
        mapOf(
            "title" to "${listName.name} の名前を変える",
            "problems" to problemsHtml(problems),
            "action" to "${listPath(user, listName)}/rename",
            "label" to "新しい名前",
            "newName" to newName,
            "backHref" to listPath(user, listName),
        ),
    )

private fun problemsHtml(problems: List<FieldProblem>): String =
    if (problems.isEmpty()) {
        ""
    } else {
        problems.joinToString(prefix = "<ul class=\"problems\">\n", postfix = "</ul>\n", separator = "") {
            "<li class=\"problem\">${escapeHtml(describeProblem(it))}</li>\n"
        }
    }

fun describeProblem(problem: FieldProblem): String {
    val label = problem.field.label
    return when (val detail = problem.problem) {
        InputProblem.Blank -> "${label}が空です"
        is InputProblem.TooLong -> "${label}は ${detail.max} 文字以内にしてください（現在 ${detail.actual} 文字）"
        is InputProblem.ForbiddenCharacter -> {
            val shown = if (detail.character.isISOControl()) "制御文字" else detail.character.toString()
            "${label}に使えない文字が含まれています（「$shown」）"
        }
    }
}

fun escapeHtml(value: String): String =
    buildString {
        value.forEach { character ->
            append(
                when (character) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '\"' -> "&quot;"
                    '\'' -> "&#39;"
                    else -> character
                },
            )
        }
    }
