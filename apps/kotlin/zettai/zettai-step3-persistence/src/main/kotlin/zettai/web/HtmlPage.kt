package zettai.web

import zettai.domain.ListName
import zettai.domain.ToDoItem

/** ToDo リストを HTML に変換する。表示の都合はドメインに持ち込まない。 */
fun renderHtml(listName: ListName, items: List<ToDoItem>): String =
    """
    <html>
      <body>
        <h1>Zettai</h1>
        <h2>${listName.name}</h2>
        <table>
          <tbody>
    ${items.joinToString("\n", transform = ::renderRow)}
          </tbody>
        </table>
      </body>
    </html>
    """.trimIndent()

private fun renderRow(item: ToDoItem): String =
    "        <tr><td>${item.description}</td><td>${item.status}</td><td>${item.dueDate ?: ""}</td></tr>"
