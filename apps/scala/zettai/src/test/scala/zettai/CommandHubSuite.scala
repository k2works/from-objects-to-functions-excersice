package zettai

import cats.effect.IO
import munit.CatsEffectSuite
import zettai.ToDoListCommand.*

class CommandHubSuite extends CatsEffectSuite:

  private val uberto = User("uberto")
  private val book   = ListName("book")

  private def withHub(body: (EventStore, CommandHub) => IO[Unit]): IO[Unit] =
    EventStore.inMemory.flatMap(store => body(store, CommandHub.commandHub(store)))

  test("成功したコマンドは、イベントを書き足す") {
    withHub { (store, commands) =>
      for
        created <- commands.execute(CreateToDoList(uberto, book))
        added   <- commands.execute(AddToDoItem(uberto, book, ToDoItem.todo("write chapter")))
        events  <- store.load
        current <- commands.current
      yield
        assertEquals(created, Right(()))
        assertEquals(added, Right(()))
        assertEquals(
          events,
          List(
            ToDoListEvent.ListCreated(uberto, book),
            ToDoListEvent.ItemAdded(uberto, book, ToDoItem.todo("write chapter"))
          )
        )
        assertEquals(
          Events.listFor(current, uberto, book),
          Some(ToDoList(book, List(ToDoItem.todo("write chapter"))))
        )
    }
  }

  test("拒否されたコマンドは、何も書き足さない") {
    withHub { (store, commands) =>
      for
        rejected <- commands.execute(AddToDoItem(uberto, book, ToDoItem.todo("write chapter")))
        events   <- store.load
      yield
        assertEquals(rejected, Left(ZettaiError.ListNotFound(uberto, book)))
        assertEquals(events, Nil)
    }
  }
