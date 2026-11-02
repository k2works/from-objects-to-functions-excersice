package zettai

import cats.effect.IO
import munit.CatsEffectSuite

class FoundationSuite extends CatsEffectSuite:

  test("同じ単語には、いつも同じ得点を返す") {
    assertEquals(Foundation.wordScore("Scala"), 5)
    assertEquals(Foundation.wordScore("Scala"), 5)
  }

  test("空の名前は、例外ではなく値として失敗を返す") {
    assertEquals(Foundation.nonBlankName(""), Left("名前は空にできません"))
    assertEquals(Foundation.nonBlankName("仕事"), Right("仕事"))
  }

  test("IO は作っただけでは実行されず、実行したときに初めて効果が起きる") {
    var calls = 0
    val load: IO[String] = IO { calls += 1; "こんにちは" }
    val program = Foundation.loadGreeting(load)
    assertEquals(calls, 0)
    program.map { greeting =>
      assertEquals(greeting, "こんにちは")
      assertEquals(calls, 1)
    }
  }
