package zettai

import cats.effect.IO

/** ログの出力先。テストは、書かれた行を集める出力先に差し替える。 */
final case class LogSink(write: String => IO[Unit])

object LogSink:

  val stdout: LogSink = LogSink(line => IO.println(line))

/** ドメインの関数にログを足さずに、入口を外から包む（デコレータ）。 */
object Logging:

  /** 失敗の種類の名前。理由も、判断に使った値も、含めない。 */
  def category(error: ZettaiError): String =
    error match
      case ZettaiError.ListNotFound(_, _)         => "ListNotFound"
      case ZettaiError.ListAlreadyExists(_, _)    => "ListAlreadyExists"
      case ZettaiError.ItemNotFound(_, _, _)      => "ItemNotFound"
      case ZettaiError.InvalidTransition(_, _, _) => "InvalidTransition"
      case ZettaiError.VersionConflict(_, _)      => "VersionConflict"
      case ZettaiError.InvalidInput(_)            => "InvalidInput"

  private def outcomeOf[A](result: Either[ZettaiError, A]): String =
    result.fold(category, _ => "ok")

  /**
   * 操作を実行し、結果にかかわらず 1 行を書く。結果そのものは、そのまま返す。
   * 想定外の失敗（IO の失敗）は、種類だけを書いて投げ直す。ログの出力先が壊れても、処理は止めない。
   */
  def observe[A](
      sink: LogSink,
      clock: IO[Long],
      kind: String,
      name: String,
      user: Option[String],
      list: Option[String],
      operation: IO[Either[ZettaiError, A]]
  ): IO[Either[ZettaiError, A]] =
    for
      startedAt  <- clock
      attempted  <- operation.attempt
      finishedAt <- clock
      outcome     = attempted.fold(_ => "UnexpectedFailure", outcomeOf)
      duration    = math.min(finishedAt - startedAt, Int.MaxValue.toLong).toInt
      line        = Json.render(Logs.toJson(LogRecord(kind, name, outcome, user, list, duration)))
      _          <- sink.write(line).handleError(_ => ())
      result     <- IO.fromEither(attempted)
    yield result

  /** 読み取りの入口を包む。 */
  def observeHub(sink: LogSink, clock: IO[Long], hub: Hub): Hub =
    Hub((user, name) =>
      observe(sink, clock, "hub", "getList", Some(user.name), Some(name.name), hub.getList(user, name))
    )

  private def commandName(command: ToDoListCommand): String =
    command match
      case ToDoListCommand.CreateToDoList(_, _)         => "CreateToDoList"
      case ToDoListCommand.AddToDoItem(_, _, _)         => "AddToDoItem"
      case ToDoListCommand.ChangeItemStatus(_, _, _, _) => "ChangeItemStatus"
      case ToDoListCommand.RenameToDoList(_, _, _)      => "RenameToDoList"

  private def commandUser(command: ToDoListCommand): String =
    command match
      case ToDoListCommand.CreateToDoList(user, _)         => user.name
      case ToDoListCommand.AddToDoItem(user, _, _)         => user.name
      case ToDoListCommand.ChangeItemStatus(user, _, _, _) => user.name
      case ToDoListCommand.RenameToDoList(user, _, _)      => user.name

  private def commandList(command: ToDoListCommand): String =
    command match
      case ToDoListCommand.CreateToDoList(_, list)         => list.name
      case ToDoListCommand.AddToDoItem(_, list, _)         => list.name
      case ToDoListCommand.ChangeItemStatus(_, list, _, _) => list.name
      case ToDoListCommand.RenameToDoList(_, list, _)      => list.name

  /** コマンドの入口を包む。コマンドの種類・利用者・リストだけを載せる。項目の説明は載せない。 */
  def observeCommandHub(sink: LogSink, clock: IO[Long], hub: CommandHub): CommandHub =
    def logged(command: ToDoListCommand, operation: IO[Either[ZettaiError, Unit]]) =
      observe(sink, clock, "command", commandName(command), Some(commandUser(command)), Some(commandList(command)), operation)
    hub.copy(
      execute = command => logged(command, hub.execute(command)),
      executeAt = (version, command) => logged(command, hub.executeAt(version, command))
    )

  /** 保管場所を包む。データベース呼び出しの結果と所要時間だけを載せる。 */
  def observeStore(sink: LogSink, clock: IO[Long], store: EventStore): EventStore =
    def logged[A](name: String, operation: IO[A], outcome: A => String): IO[A] =
      for
        startedAt  <- clock
        attempted  <- operation.attempt
        finishedAt <- clock
        label       = attempted.fold(_ => "UnexpectedFailure", outcome)
        duration    = math.min(finishedAt - startedAt, Int.MaxValue.toLong).toInt
        line        = Json.render(Logs.toJson(LogRecord("store", name, label, None, None, duration)))
        _          <- sink.write(line).handleError(_ => ())
        result     <- IO.fromEither(attempted)
      yield result
    EventStore(
      logged("load", store.load, _ => "ok"),
      (expected, events) => logged("append", store.append(expected, events), outcomeOf)
    )
