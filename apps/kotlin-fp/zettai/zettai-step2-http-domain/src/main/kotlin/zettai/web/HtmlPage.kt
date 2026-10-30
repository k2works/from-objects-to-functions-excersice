package zettai.web

import zettai.domain.ToDoList

fun renderHtml(todoList: ToDoList): String =
    """
    <html>
      <body>
        <h1>Zettai</h1>
        <h2>${escapeHtml(todoList.listName.name)}</h2>
        <ul>
    ${todoList.items.joinToString("\n") { "      <li>${escapeHtml(it.description)}</li>" }}
        </ul>
      </body>
    </html>
    """.trimIndent()

private fun escapeHtml(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")
