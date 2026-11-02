package zettai.web

import zettai.domain.{ListName, ToDoItem, ToDoList}

import munit.FunSuite

class HtmlSuite extends FunSuite:

  test("リストを HTML にする") {
    val list = ToDoList(ListName("book"), List(ToDoItem.todo("write chapter")))
    assertEquals(
      Html.render(list),
      "<html><body><h1>Zettai</h1><h2>book</h2><ul><li>write chapter</li></ul></body></html>"
    )
  }

  test("特殊文字はエスケープする") {
    assertEquals(Html.escape("a & <b> \"c\""), "a &amp; &lt;b&gt; &quot;c&quot;")
  }
