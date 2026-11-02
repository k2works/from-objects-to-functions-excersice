package zettai

import cats.effect.IO
import cats.syntax.all.*
import munit.CatsEffectSuite

class PostgresStoreSuite extends CatsEffectSuite:

  private val uberto  = User("uberto")
  private val book    = ListName("book")
  private val created = ToDoListEvent.ListCreated(uberto, book)
  private val added   = ToDoListEvent.ItemAdded(uberto, book, ToDoItem.todo("a & <b>"))
  private val changed = ToDoListEvent.ItemStatusChanged(uberto, book, "a & <b>", ToDoStatus.Done)

  private val xa = IO(PostgresStore.transactor(TestDatabase.config))

  /** テストごとに専用のテーブルを作り、終わったら捨てる。 */
  private def withTable(body: (cats.effect.IO[EventStore], String) => IO[Unit]): IO[Unit] =
    for
      transactor <- xa
      table      <- PostgresStore.freshTableName
      _          <- body(PostgresStore.open(transactor, table), table).guarantee(PostgresStore.dropTable(transactor, table))
    yield ()

  test("書いたイベントを、同じ順序で読み戻せる") {
    withTable { (open, _) =>
      for
        store <- open
        _     <- store.append(0, List(created, added))
        _     <- store.append(2, List(changed))
        read  <- store.load
      yield assertEquals(read, Versioned(3, List(created, added, changed)))
    }
  }

  test("別の接続（別のストア）からも、書かれたものが見える") {
    withTable { (open, _) =>
      for
        writer <- open
        reader <- open
        _      <- writer.append(0, List(created))
        read   <- reader.load
      yield assertEquals(read, Versioned(1, List(created)))
    }
  }

  test("古い版で書こうとすると断られ、何も書かれない") {
    withTable { (open, _) =>
      for
        store    <- open
        _        <- store.append(0, List(created))
        rejected <- store.append(0, List(added))
        read     <- store.load
      yield
        assertEquals(rejected, Left(ZettaiError.VersionConflict(0, 1)))
        assertEquals(read, Versioned(1, List(created)))
    }
  }

  test("2 つの接続が同じ版から同時に書くと、ちょうど一方だけが成功し、負けた側は何も残さない") {
    withTable { (open, _) =>
      for
        a       <- open
        b       <- open
        results <- IO.both(a.append(0, List(created, added)), b.append(0, List(created)))
        read    <- a.load
      yield
        val outcomes = List(results._1, results._2)
        assertEquals(outcomes.count(_.isRight), 1)
        assert(read.version == 1 || read.version == 2)
        assertEquals(read.events.size, read.version)
    }
  }

  test("危ないテーブル名は、SQL を組み立てる前に断る") {
    xa.flatMap { transactor =>
      PostgresStore.open(transactor, "events; drop table users").attempt.map { outcome =>
        assert(outcome.isLeft)
        assertEquals(PostgresStore.validTableName("events; drop table users"), false)
        assertEquals(PostgresStore.validTableName("events_abc_123"), true)
      }
    }
  }
