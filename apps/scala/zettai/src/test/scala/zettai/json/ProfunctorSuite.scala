package zettai.json

import cats.arrow.Profunctor
import munit.FunSuite

class ProfunctorSuite extends FunSuite:

  private val domain: List[Int]      = List(Int.MinValue, -3, -1, 0, 1, 2, 7, Int.MaxValue / 2)
  private val pre: List[Int => Int]  = List(x => x, x => x / 2, x => 0 - x, x => x * 0 + 5)
  private val post: List[Int => Int] = List(x => x, x => x / 3, x => 0 - x, x => x * 0 + 1)
  private val base: List[Int => Int] = List(x => x + 1, x => x / 2, x => 0 - x)

  test("dimap に恒等関数を渡しても何も変わらない") {
    for f <- base; x <- domain do assertEquals(Dimap.dimap(identity[Int], identity[Int], f)(x), f(x))
  }

  test("dimap は合成できる。入力側は逆向きに、出力側は順向きに") {
    for f <- base; p1 <- pre; p2 <- pre; q1 <- post; q2 <- post; x <- domain do
      assertEquals(
        Dimap.dimap(p2, q2, Dimap.dimap(p1, q1, f))(x),
        Dimap.dimap(p2 andThen p1, q1 andThen q2, f)(x)
      )
  }

  test("入力は逆向きに、出力は順向きに写る") {
    val shout = Dimap.dimap((s: String) => s.length, (n: Int) => "*" * n, (n: Int) => n + 1)
    assertEquals(shout("abc"), "****")
  }

  test("自作の dimap は、Cats の Profunctor の dimap と一致する") {
    for f <- base; p <- pre; q <- post; x <- domain do
      assertEquals(Dimap.dimap(p, q, f)(x), Profunctor[Function1].dimap(f)(p)(q)(x))
  }
