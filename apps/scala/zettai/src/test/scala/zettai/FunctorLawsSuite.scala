package zettai

import cats.Functor
import munit.FunSuite

class FunctorLawsSuite extends FunSuite:

  private type Result[A] = Either[ZettaiError, A]

  private val uberto = User("uberto")
  private val book   = ListName("book")

  /** 端の値と、4 種類の失敗を含む。 */
  private val samples: List[Result[Int]] =
    List(Right(0), Right(1), Right(Int.MinValue), Right(Int.MaxValue)) ++ List[Result[Int]](
      Left(ZettaiError.ListNotFound(uberto, book)),
      Left(ZettaiError.ListAlreadyExists(uberto, book)),
      Left(ZettaiError.ItemNotFound(uberto, book, "x")),
      Left(ZettaiError.InvalidTransition("x", ToDoStatus.Todo, ToDoStatus.Blocked))
    )

  private val f: Int => Int = _ + 3
  private val g: Int => Int = _ * 2

  test("恒等則: map(identity) は何も変えない") {
    for sample <- samples do assertEquals(Functor[Result].map(sample)(identity), sample)
  }

  test("合成則: map(f).map(g) は map(g compose f) と同じ") {
    for sample <- samples do
      assertEquals(
        Functor[Result].map(Functor[Result].map(sample)(f))(g),
        Functor[Result].map(sample)(f andThen g)
      )
  }

  /** 壊れた map。成功値に関数を 2 回当てる。 */
  private def brokenMap[A](result: Result[A])(h: A => A): Result[A] = result.map(h).map(h)

  test("壊れた map は、恒等則を通るが、合成則で落ちる") {
    assert(samples.forall(s => brokenMap(s)(identity) == s))
    assert(samples.exists(s => brokenMap(brokenMap(s)(f))(g) != brokenMap(s)(f andThen g)))
  }
