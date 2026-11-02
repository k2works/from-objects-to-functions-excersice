package zettai

import munit.FunSuite
import zettai.JsonTree.*

class JsonSuite extends FunSuite:

  test("木を文字列に書き出す") {
    assertEquals(
      Json.render(JObject(List("name" -> JString("book"), "count" -> JInt(2), "none" -> JNull, "items" -> JArray(List(JInt(1), JInt(-2)))))),
      """{"name":"book","count":2,"none":null,"items":[1,-2]}"""
    )
  }

  test("特殊文字はエスケープして書き出す") {
    assertEquals(Json.render(JString("a\"b\\c\nd\u0001")), "\"a\\\"b\\\\c\\nd\\u0001\"")
  }

  test("文字列から木を読み込む") {
    assertEquals(
      Json.parse(""" { "a" : [ 1 , "x" , null ] , "b" : { } } """),
      Right(JObject(List("a" -> JArray(List(JInt(1), JString("x"), JNull)), "b" -> JObject(Nil))))
    )
  }

  test("読めないものは、例外ではなく失敗の値になる") {
    for bad <- List("", "{", "[1,", "{\"a\":}", "tru", "\"abc", "1 2", "{\"a\" 1}", "[1,]", "-", "2147483648", "-2147483649") do
      assert(Json.parse(bad).isLeft, s"読めてしまった: $bad")
  }

  test("Int の最小値と最大値を、書き出して読み戻せる") {
    for n <- List(Int.MinValue, Int.MaxValue, 0, -1, 1) do assertEquals(Json.parse(Json.render(JInt(n))), Right(JInt(n)))
  }

  private val strings = List("", "a", "a b", "日本語", "\"", "\\", "\n\t\r", "\u0000\u001f", "😀")

  test("書き出して読み戻すと、元の木に戻る（小さな範囲の全パターン）") {
    val leaves: List[JsonTree] = JNull :: List(0, -1, Int.MinValue, Int.MaxValue).map(JInt(_)) ::: strings.map(JString(_))
    val arrays  = for a <- leaves; b <- leaves yield JArray(List(a, b))
    val objects = for k <- strings; v <- leaves yield JObject(List(k -> v))
    val nested  = for a <- arrays.take(20); o <- objects.take(20) yield JObject(List("a" -> a, "o" -> o))
    for tree <- leaves ::: arrays ::: objects ::: nested do assertEquals(Json.parse(Json.render(tree)), Right(tree))
  }
