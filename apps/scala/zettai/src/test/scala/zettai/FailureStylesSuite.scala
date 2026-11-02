package zettai

import cats.effect.IO
import munit.CatsEffectSuite

/** 業務の失敗を IO の失敗（例外）で運ぶ版との比較。 */
class FailureStylesSuite extends CatsEffectSuite:

  final case class ZettaiException(error: ZettaiError) extends Exception(error.toString)

  private val hub     = Hub.inMemory(Map.empty)
  private val missing = ZettaiError.ListNotFound(User("uberto"), ListName("book"))

  /** Either を IO の失敗に押し込む。型の上では、失敗の種類が見えなくなる。 */
  private def raising(user: User, name: ListName): IO[ToDoList] =
    hub.getList(user, name).flatMap(found => IO.fromEither(found.left.map(ZettaiException(_))))

  test("Either で運ぶと、業務の失敗は IO の成功の中に値として残る") {
    hub.getList(User("uberto"), ListName("book")).attempt.map { outcome =>
      assertEquals(outcome, Right(Left(missing)))
    }
  }

  test("IO の失敗で運ぶと、業務の失敗は例外になり、他の失敗と区別がつかない") {
    raising(User("uberto"), ListName("book")).attempt.map { outcome =>
      assertEquals(outcome.left.map(_.getMessage), Left(missing.toString))
    }
  }

  test("例外で運ぶ版でも、受け取る側が型を戻せば、同じ失敗を取り出せる") {
    raising(User("uberto"), ListName("book")).attempt.map { outcome =>
      assertEquals(
        outcome.left.toOption.collect { case ZettaiException(error) => error },
        Some(missing)
      )
    }
  }
