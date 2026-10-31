package zettai.web

import zettai.domain.ToDoList

fun renderHtml(list: ToDoList): String =
    buildString {
        appendLine("<!doctype html>")
        appendLine("<html lang=\"ja\">")
        appendLine("<head><meta charset=\"utf-8\"><title>${escapeHtml(list.listName.name)}</title></head>")
        appendLine("<body>")
        appendLine("<h1>${escapeHtml(list.listName.name)}</h1>")
        appendLine("<ul>")
        list.items.forEach { item ->
            appendLine("<li>${escapeHtml(item.description)}</li>")
        }
        appendLine("</ul>")
        appendLine("</body>")
        append("</html>")
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
