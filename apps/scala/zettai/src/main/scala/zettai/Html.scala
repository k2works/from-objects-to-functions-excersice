package zettai

object Html:

  def escape(text: String): String =
    text
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")

  private def renderItem(item: ToDoItem): String =
    s"<li>${escape(item.description)}</li>"

  def render(list: ToDoList): String =
    val lis = list.items.map(renderItem).mkString
    s"<html><body><h1>Zettai</h1><h2>${escape(list.name.name)}</h2><ul>$lis</ul></body></html>"
