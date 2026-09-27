package zettai.web

import zettai.domain.ToDoItem
import zettai.domain.ToDoList

/** ToDo リストを HTML に変換する。表示の都合はドメインに持ち込まない。 */
fun renderHtml(todoList: ToDoList): String =
    """
    <html>
      <body>
        <h1>Zettai</h1>
        <h2>${todoList.listName.name}</h2>
        <table>
          <tbody>
    ${todoList.items.joinToString("\n", transform = ::renderRow)}
          </tbody>
        </table>
      </body>
    </html>
    """.trimIndent()

private fun renderRow(item: ToDoItem): String =
    "        <tr><td>${item.description}</td><td>${item.status}</td><td>${item.dueDate ?: ""}</td></tr>"
