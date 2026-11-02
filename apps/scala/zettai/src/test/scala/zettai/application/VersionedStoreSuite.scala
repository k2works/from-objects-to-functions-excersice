package zettai.application

import zettai.domain.{ListName, ToDoItem, User}
import zettai.domain.commands.ToDoListCommand
import zettai.domain.errors.ZettaiError
import zettai.domain.events.ToDoListEvent

import cats.effect.IO
import munit.CatsEffectSuite

class VersionedStoreSuite extends CatsEffectSuite:

  private val uberto  = User("uberto")
  private val book    = ListName("book")
  private val created = ToDoListEvent.ListCreated(uberto, book)
  private val added   = ToDoListEvent.ItemAdded(uberto, book, ToDoItem.todo("a"))

  test("空のストアの版は 0") {
    EventStore.inMemory.flatMap(_.load).map(read => assertEquals(read, Versioned(0, Nil)))
  }

  test("正しい版で書き足すと、版がイベントの数だけ進む") {
    for
      store  <- EventStore.inMemory
      first  <- store.append(0, List(created))
      second <- store.append(1, List(added))
      read   <- store.load
    yield
      assertEquals(first, Right(()))
      assertEquals(second, Right(()))
      assertEquals(read, Versioned(2, List(created, added)))
  }

  test("古い版で書こうとすると断られ、何も書かれない") {
    for
      store    <- EventStore.inMemory
      _        <- store.append(0, List(created))
      rejected <- store.append(0, List(added))
      read     <- store.load
    yield
      assertEquals(rejected, Left(ZettaiError.VersionConflict(0, 1)))
      assertEquals(read, Versioned(1, List(created)))
  }

  test("同時に同じ版から書くと、ちょうど一方だけが成功する") {
    for
      store   <- EventStore.inMemory
      results <- IO.both(store.append(0, List(created)), store.append(0, List(added)))
      read    <- store.load
    yield
      val outcomes = List(results._1, results._2)
      assertEquals(outcomes.count(_.isRight), 1)
      assertEquals(read.events.size, 1)
  }

  test("コマンドの入口は、古い版のまま実行された競合を VersionConflict にする") {
    for
      store    <- EventStore.inMemory
      commands  = CommandHub.commandHub(store)
      _        <- store.append(0, List(created))
      stale    <- commands.executeAt(0, ToDoListCommand.AddToDoItem(uberto, book, ToDoItem.todo("a")))
    yield assertEquals(stale, Left(ZettaiError.VersionConflict(0, 1)))
  }
