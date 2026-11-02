package zettai.json

import cats.Invariant
import munit.FunSuite
import zettai.json.JsonTree.*

class ConverterSuite extends FunSuite:

  test("文字列の変換は、書く向きと読む向きが対になる") {
    assertEquals(Converter.string.encode("x"), JString("x"))
    assertEquals(Converter.string.decode(JString("x")), Right("x"))
    assertEquals(Converter.string.decode(JInt(1)), Left("not a string"))
  }

  test("型を変えるには、行きと戻りの両方向の写像が要る（imap）") {
    val lengthOf = Invariant[Converter].imap(Converter.string)(_.length)("x".repeat(_))
    assertEquals(lengthOf.encode(3), JString("xxx"))
    assertEquals(lengthOf.decode(JString("xxx")), Right(3))
  }

  test("imap の恒等則と合成則") {
    val c      = Converter.string
    val same   = Invariant[Converter].imap(c)(identity[String])(identity[String])
    val f: String => Int = _.length
    val g: Int => String = "x".repeat(_)
    val once  = Invariant[Converter].imap(c)(f andThen (_ + 1))(((n: Int) => n - 1) andThen g)
    val twice = Invariant[Converter].imap(Invariant[Converter].imap(c)(f)(g))((n: Int) => n + 1)((n: Int) => n - 1)
    for s <- List("", "a", "abc") do
      assertEquals(same.encode(s), c.encode(s))
      assertEquals(same.decode(JString(s)), c.decode(JString(s)))
    for n <- List(0, 1, 3) do
      assertEquals(once.encode(n + 1), twice.encode(n + 1))
      assertEquals(once.decode(JString("x".repeat(n))), twice.decode(JString("x".repeat(n))))
  }
