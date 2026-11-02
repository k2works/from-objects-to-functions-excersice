package zettai.domain.validation

import zettai.domain.{ListName, User}
import zettai.domain.commands.ToDoListCommand

import cats.Applicative
import cats.data.{NonEmptyChain, Validated, ValidatedNec}
import cats.syntax.all.*
import munit.FunSuite

class ValidationSuite extends FunSuite:

  private type V[A] = ValidatedNec[FieldProblem, A]

  private val A = Applicative[[X] =>> ValidatedNec[FieldProblem, X]]

  test("正しい入力は、コマンドになる") {
    assertEquals(
      Validate.validateRename("uberto", "book", "shop"),
      Validated.valid(ToDoListCommand.RenameToDoList(User("uberto"), ListName("book"), ListName("shop")))
    )
  }

  test("1 つの入力の誤りは、規則の順に集める") {
    assertEquals(
      Validate.validateName(InputField.NewName, "a/" + "b" * 40).leftMap(_.toList),
      Validated.invalid(List(FieldProblem.TooLong(InputField.NewName, 40), FieldProblem.ForbiddenCharacter(InputField.NewName)))
    )
  }

  test("3 つの入力の誤りは、最初の 1 つで止めず、入力の順にすべて集める") {
    assertEquals(
      Validate.validateRename("", "a/b", " ").leftMap(_.toList),
      Validated.invalid(
        List(
          FieldProblem.Blank(InputField.UserName),
          FieldProblem.ForbiddenCharacter(InputField.ListNameField),
          FieldProblem.Blank(InputField.NewName)
        )
      )
    )
  }

  test("Either の連鎖では、最初の誤りで止まる。比べると、集めている数が違う") {
    val viaEither: Either[FieldProblem, ToDoListCommand] =
      for
        user    <- Validate.validateName(InputField.UserName, "").toEither.leftMap(_.head)
        list    <- Validate.validateName(InputField.ListNameField, "").toEither.leftMap(_.head)
        newName <- Validate.validateName(InputField.NewName, "").toEither.leftMap(_.head)
      yield ToDoListCommand.RenameToDoList(User(user), ListName(list), ListName(newName))
    assertEquals(viaEither, Left(FieldProblem.Blank(InputField.UserName)))
    assertEquals(Validate.validateRename("", "", "").fold(_.length, _ => 0L), 3L)
  }

  test("利用者に見せる説明に、入力の値を含めない") {
    val text = Validate.explain(Validate.validateRename("secret/user", "", "ok").fold(_.toList, _ => Nil))
    assert(!text.contains("secret"))
    assert(text.contains("利用者名に使えない文字が含まれています"))
    assert(text.contains("リスト名は空にできません"))
  }

  // ---- アプリカティブの法則 ----

  private val samples: List[V[Int]] = List(
    Validated.valid(0),
    Validated.valid(Int.MaxValue),
    Validated.invalid(NonEmptyChain(FieldProblem.Blank(InputField.UserName))),
    Validated.invalid(NonEmptyChain(FieldProblem.Blank(InputField.UserName), FieldProblem.TooLong(InputField.NewName, 40)))
  )

  private val f: V[Int => Int] = Validated.valid(_ + 3)
  private val g: V[Int => Int] = Validated.valid(_ * 2)
  private val bad: V[Int => Int] = Validated.invalid(NonEmptyChain(FieldProblem.ForbiddenCharacter(InputField.ListNameField)))

  test("恒等則: pure(identity) を適用しても何も変わらない") {
    for v <- samples do assertEquals(A.ap(A.pure[Int => Int](identity))(v), v)
  }

  test("準同型則: pure(f) を pure(x) に適用すると pure(f(x))") {
    for x <- List(0, 1, 41) do assertEquals(A.ap(A.pure[Int => Int](_ + 3))(A.pure(x)), A.pure(x + 3))
  }

  test("交換則: 関数と値の順序を入れ替えても同じ") {
    for fn <- List(f, bad); x <- List(0, 41) do
      assertEquals(A.ap(fn)(A.pure(x)), A.ap(A.pure[(Int => Int) => Int](h => h(x)))(fn))
  }

  test("合成則: 関数を先に合成してから適用しても、順に適用しても同じ（誤りの集まり方も含めて）") {
    val compose: V[(Int => Int) => (Int => Int) => Int => Int] = A.pure(h => k => x => h(k(x)))
    for u <- List(f, bad); v <- List(g, bad); w <- samples do
      assertEquals(A.ap(A.ap(A.ap(compose)(u))(v))(w), A.ap(u)(A.ap(v)(w)))
  }

  // ---- 壊れた合成 ----

  /** 壊れた合成。左の誤りだけを返し、右の誤りを捨てる。 */
  private def brokenAp[A, B](ff: V[A => B])(fa: V[A]): V[B] =
    (ff, fa) match
      case (Validated.Valid(h), Validated.Valid(a)) => Validated.valid(h(a))
      case (Validated.Invalid(e), _)                => Validated.invalid(e)
      case (_, Validated.Invalid(e))                => Validated.invalid(e)

  test("壊れた合成は、成功と片側の失敗では Validated と一致するが、両側が失敗したときに誤りを落とす") {
    assert(samples.forall(v => brokenAp(A.pure[Int => Int](identity))(v) == v))
    assertNotEquals(brokenAp(bad)(samples(3)), A.ap(bad)(samples(3)))
  }
