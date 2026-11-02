package zettai

import cats.effect.IO
import cats.syntax.all.*
import munit.CatsEffectSuite
import org.typelevel.doobie.*
import org.typelevel.doobie.implicits.*

class ConnectionIOSuite extends CatsEffectSuite:

  private val xa = PostgresStore.transactor(TestDatabase.config)

  private def plusOne(n: Int): ConnectionIO[Int] = sql"select ${n} + 1".query[Int].unique
  private def timesTwo(n: Int): ConnectionIO[Int] = sql"select ${n} * 2".query[Int].unique

  test("モナド則: 左単位元") {
    List(0, 1, Int.MaxValue - 1).traverse_ { a =>
      for
        left  <- a.pure[ConnectionIO].flatMap(plusOne).transact(xa)
        right <- plusOne(a).transact(xa)
      yield assertEquals(left, right)
    }
  }

  test("モナド則: 右単位元") {
    List(0, 1, 41).traverse_ { a =>
      for
        left  <- plusOne(a).flatMap(_.pure[ConnectionIO]).transact(xa)
        right <- plusOne(a).transact(xa)
      yield assertEquals(left, right)
    }
  }

  test("モナド則: 結合律") {
    List(0, 1, 41).traverse_ { a =>
      for
        left  <- plusOne(a).flatMap(plusOne).flatMap(timesTwo).transact(xa)
        right <- plusOne(a).flatMap(n => plusOne(n).flatMap(timesTwo)).transact(xa)
      yield assertEquals(left, right)
    }
  }

  test("途中で失敗すると、同じトランザクションの前の書き込みも取り消される") {
    for
      table <- PostgresStore.freshTableName
      t      = Fragment.const(table)
      _     <- (fr"create table" ++ t ++ fr"(n int primary key)").update.run.transact(xa)
      boom   = (fr"insert into" ++ t ++ fr"values (1)").update.run *>
                 FC.raiseError[Int](new RuntimeException("途中の失敗"))
      failed <- boom.transact(xa).attempt
      count  <- (fr"select count(*) from" ++ t).query[Int].unique.transact(xa)
      _     <- PostgresStore.dropTable(xa, table)
    yield
      assert(failed.isLeft)
      assertEquals(count, 0)
  }
