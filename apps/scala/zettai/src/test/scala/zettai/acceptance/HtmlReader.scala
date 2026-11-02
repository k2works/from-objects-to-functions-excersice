package zettai.acceptance

import zettai.domain.ToDoItem
import zettai.web.Html

/** 返ってきた HTML から項目の説明を読み戻す。Html.escape の逆。 */
object HtmlReader:

  private val item = "<li>(.*?)</li>".r

  def unescape(text: String): String =
    text
      .replace("&quot;", "\"")
      .replace("&gt;", ">")
      .replace("&lt;", "<")
      .replace("&amp;", "&")

  def items(html: String): List[ToDoItem] =
    item.findAllMatchIn(html).map(m => ToDoItem.todo(unescape(m.group(1)))).toList
