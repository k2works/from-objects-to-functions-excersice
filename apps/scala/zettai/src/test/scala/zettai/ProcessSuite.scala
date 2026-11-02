package zettai

import cats.effect.{IO, IOLocal}
import cats.syntax.all.*
import munit.CatsEffectSuite
import zettai.ToDoListCommand.*

class ProcessSuite extends CatsEffectSuite:

  private val uberto = User("uberto")
  private val book   = ListName("book")

  private val commands = List(
    CreateToDoList(uberto, book),
    AddToDoItem(uberto, book, ToDoItem.todo("write")),
    AddToDoItem(uberto, ListName("missing"), ToDoItem.todo("x")),
    ChangeItemStatus(uberto, book, "write", ToDoStatus.Done)
  )

  test("Kleisli: 文脈として渡したストアに、コマンドの入口と同じ結果が出る") {
    for
      a      <- EventStore.inMemory
      b      <- EventStore.inMemory
      viaHub <- commands.traverse(CommandHub.commandHub(a).execute)
      viaCtx <- commands.traverse(Process.process(_).run(b))
      readA  <- a.load
      readB  <- b.load
    yield
      assertEquals(viaCtx, viaHub)
      assertEquals(readB, readA)
  }

  test("Kleisli: 同じ処理を、別のストアに差し替えて実行できる") {
    for
      a <- EventStore.inMemory
      b <- EventStore.inMemory
      program = Process.process(CreateToDoList(uberto, book))
      _     <- program.run(a)
      readA <- a.load
      readB <- b.load
    yield
      assertEquals(readA.version, 1)
      assertEquals(readB.version, 0)
  }

  private def processWithLocal(local: IOLocal[Option[EventStore]], command: ToDoListCommand) =
    local.get.flatMap {
      case Some(store) => Process.process(command).run(store)
      case None        => IO.raiseError(new IllegalStateException("文脈（EventStore）が設定されていません"))
    }

  test("IOLocal: 設定した文脈を、引数なしの処理が読む") {
    for
      store <- EventStore.inMemory
      local <- IOLocal[Option[EventStore]](None)
      _     <- local.set(Some(store))
      done  <- processWithLocal(local, CreateToDoList(uberto, book))
      read  <- store.load
    yield
      assertEquals(done, Right(()))
      assertEquals(read.version, 1)
  }

  test("IOLocal: 文脈の設定し忘れは型に現れず、実行時に失敗する") {
    for
      local   <- IOLocal[Option[EventStore]](None)
      outcome <- processWithLocal(local, CreateToDoList(uberto, book)).attempt
    yield assertEquals(outcome.left.map(_.getMessage), Left("文脈（EventStore）が設定されていません"))
  }

  test("IOLocal: 子ファイバーは親の値を引き継ぎ、子の変更は親に戻らない") {
    for
      parentStore <- EventStore.inMemory
      childStore  <- EventStore.inMemory
      local       <- IOLocal[Option[EventStore]](None)
      _           <- local.set(Some(parentStore))
      inherited   <- local.get.start.flatMap(_.joinWithNever)
      _           <- (local.set(Some(childStore)) *> local.get).start.flatMap(_.joinWithNever)
      afterChild  <- local.get
    yield
      assertEquals(inherited.exists(_ eq parentStore), true)
      assertEquals(afterChild.exists(_ eq parentStore), true)
  }

  test("バッチ: すべて成功すると、1 回の書き込みで版がイベントの数だけ進む") {
    val batch = List(
      CreateToDoList(uberto, book),
      AddToDoItem(uberto, book, ToDoItem.todo("a")),
      AddToDoItem(uberto, book, ToDoItem.todo("b"))
    )
    for
      store  <- EventStore.inMemory
      result <- Process.processAll(batch).run(store)
      read   <- store.load
    yield
      assertEquals(result, Right(()))
      assertEquals(read.version, 3)
  }

  test("バッチ: 1 つでも拒否されたら、成功していたコマンドの分も書かれない") {
    val batch = List(
      CreateToDoList(uberto, book),
      AddToDoItem(uberto, book, ToDoItem.todo("a")),
      AddToDoItem(uberto, ListName("missing"), ToDoItem.todo("b"))
    )
    for
      store  <- EventStore.inMemory
      result <- Process.processAll(batch).run(store)
      read   <- store.load
    yield
      assertEquals(result, Left(ZettaiError.ListNotFound(uberto, ListName("missing"))))
      assertEquals(read, Versioned(0, Nil))
  }

  test("バッチ: 前のコマンドが作った状態を、あとのコマンドが見る") {
    val batch = List(CreateToDoList(uberto, book), ChangeItemStatus(uberto, book, "nothing", ToDoStatus.Done))
    for
      store  <- EventStore.inMemory
      result <- Process.processAll(batch).run(store)
    yield assertEquals(result, Left(ZettaiError.ItemNotFound(uberto, book, "nothing")))
  }

  test("バッチ: 読んだあとに別の書き手が書いていたら、全体が VersionConflict で断られ、バッチの分は書かれない") {
    val other = ToDoListEvent.ListCreated(User("alice"), ListName("other"))
    val batch = List(CreateToDoList(uberto, book), AddToDoItem(uberto, book, ToDoItem.todo("a")))
    for
      inner <- EventStore.inMemory
      // 読み出しのあと、書き込みの直前に、別の書き手が割り込む店
      interrupted = inner.copy(append = (expected, events) => inner.append(0, List(other)) *> inner.append(expected, events))
      result <- Process.processAll(batch).run(interrupted)
      read   <- inner.load
    yield
      assertEquals(result, Left(ZettaiError.VersionConflict(0, 1)))
      assertEquals(read, Versioned(1, List(other)))
  }
