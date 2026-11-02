package zettai

import munit.FunSuite
import zettai.JsonTree.*

class LogRecordSuite extends FunSuite:

  private val record = LogRecord("command", "AddToDoItem", "ok", Some("uberto"), Some("book"), 12)

  test("ログの 1 行は、決まった 6 つの欄だけを持つ") {
    assertEquals(
      Json.render(Logs.toJson(record)),
      """{"kind":"command","name":"AddToDoItem","outcome":"ok","user":"uberto","list":"book","durationMs":12}"""
    )
  }

  test("書き出して読み戻すと、元の記録に戻る") {
    for r <- List(record, record.copy(user = None, list = None), record.copy(durationMs = Int.MaxValue)) do
      assertEquals(Logs.fromJson(Logs.toJson(r)), Right(r))
  }

  test("欄が足りない行は、失敗の値になる") {
    assertEquals(Logs.fromJson(JObject(List("kind" -> JString("x")))), Left("missing field: name"))
    assertEquals(Logs.fromJson(JInt(1)), Left("a log line must be an object"))
  }
