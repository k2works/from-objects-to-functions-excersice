package zettai.web

import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.fp.Outcome
import zettai.ui.ListTag
import zettai.ui.StringTag
import zettai.ui.Template
import zettai.ui.TemplateError
import zettai.ui.TemplateTag

/**
 * ToDo リストの画面。
 *
 * 第 11 章でテンプレート機構に置き換えた。タグを書いたのにデータを渡し忘れると
 * 失敗になるので、画面が壊れる前に気づける。
 */
private val listPage = Template(
    """
    <html>
      <body>
        <h1>Zettai</h1>
        <h2>{{listName}}</h2>
        <table>
          <tbody>
    {{#items}}        <tr><td>{{description}}</td><td>{{status}}</td><td>{{dueDate}}</td></tr>
    {{/items}}      </tbody>
        </table>
      </body>
    </html>
    """.trimIndent()
)

fun renderHtml(listName: ListName, items: List<ToDoItem>): Outcome<TemplateError, String> =
    listPage.render(
        mapOf(
            "listName" to StringTag(listName.name),
            "items" to ListTag(items.map(::itemRow))
        )
    )

private fun itemRow(item: ToDoItem): Map<String, TemplateTag> =
    mapOf(
        "description" to StringTag(item.description),
        "status" to StringTag(item.status.toString()),
        "dueDate" to StringTag(item.dueDate?.toString() ?: "")
    )
