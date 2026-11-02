package zettai

import cats.Functor
import cats.data.Nested
import cats.effect.IO
import munit.CatsEffectSuite

class QueryFunctorSuite extends CatsEffectSuite:

  private val book = ListName("book")

  private val rows: List[List[ToDoListRow]] =
    List(Nil, List(ToDoListRow(book, 0, 0)), List(ToDoListRow(book, 2, 1), ToDoListRow(ListName("food"), 0, 0)))

  private val total: ToDoListRow => Int   = _.total
  private val show: Int => String         = n => s"$n items"

  /** IO の中の List を、二重の map で変換する。 */
  private type Query[A] = Nested[IO, List, A]

  private def query(value: List[ToDoListRow]): Query[ToDoListRow] = Nested(IO.pure(value))

  test("二重の map は恒等則を満たす") {
    rows.foldLeft(IO.unit) { (done, value) =>
      done *> Functor[Query].map(query(value))(identity).value.map(assertEquals(_, value))
    }
  }

  test("二重の map は合成則を満たす") {
    val F = Functor[Query]
    rows.foldLeft(IO.unit) { (done, value) =>
      for
        _      <- done
        twice  <- F.map(F.map(query(value))(total))(show).value
        once   <- F.map(query(value))(total andThen show).value
      yield assertEquals(twice, once)
    }
  }

  test("IO の失敗は、二重の層をそのまま通り抜ける") {
    val failing = Nested[IO, List, ToDoListRow](IO.raiseError(new RuntimeException("down")))
    Functor[Query].map(failing)(total).value.attempt.map { outcome =>
      assertEquals(outcome.left.map(_.getMessage), Left("down"))
    }
  }
