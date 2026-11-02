pub mod Adapter.Logging {

    use Domain.Commands.ToDoListCommand
    use Application.CommandHub.CommandHub
    use Domain.{User, ListName}
    use Domain.Errors.ZettaiError
    use Application.EventStore.EventStore
    use Application.Hub.ToDoListHub
    use Logging.Logs.LogRecord
    use Application.QueryHub.QueryHub

    /// 失敗の種類の名前。理由も、判断に使った値も、含めない。
    pub def category(error: ZettaiError): String =
        match error {
            case ZettaiError.ListNotFound(_, _)         => "ListNotFound"
            case ZettaiError.ListAlreadyExists(_, _)    => "ListAlreadyExists"
            case ZettaiError.ItemNotFound(_, _, _)      => "ItemNotFound"
            case ZettaiError.InvalidTransition(_, _, _) => "InvalidTransition"
            case ZettaiError.StorageFailure(_)          => "StorageFailure"
            case ZettaiError.VersionConflict(_, _)      => "VersionConflict"
            case ZettaiError.InvalidInput(_)            => "InvalidInput"
        }

    def outcomeOf(result: Result[ZettaiError, a]): String =
        match result {
            case Ok(_)    => "ok"
            case Err(e)   => category(e)
        }

    /// 操作を実行し、結果にかかわらず 1 行を書く。結果そのものは、そのまま返す。
    pub def observe(clock: Unit -> Int64 \ ec, kind: String, name: String, user: Option[String], list: Option[String],
                    operation: Unit -> Result[ZettaiError, a] \ ef): Result[ZettaiError, a] \ ec + ef + Logger = {
        let startedAt = clock();
        let result = operation();
        let finishedAt = clock();
        let record = LogRecord.LogRecord(kind, name, outcomeOf(result), user, list, Option.getWithDefault(Int32.maxValue(), Int64.tryToInt32(finishedAt - startedAt)));
        Logger.info(Json.render(Logging.Logs.toJson(record)));
        result
    }

    def userName(user: User): String = match user { case User.User(n) => n }

    def listName(list: ListName): String = match list { case ListName.ListName(n) => n }

    /// 読み取りの入口を包む。
    pub def observeHub(clock: Unit -> Int64 \ ec, hub: ToDoListHub[ef]): ToDoListHub[ec + ef + Logger] = {
        getList = (user, name) ->
            observe(clock, "hub", "getList", Some(userName(user)), Some(listName(name)), () -> hub#getList(user, name))
    }

    def commandName(command: ToDoListCommand): String =
        match command {
            case ToDoListCommand.CreateToDoList(_, _)         => "CreateToDoList"
            case ToDoListCommand.AddToDoItem(_, _, _)         => "AddToDoItem"
            case ToDoListCommand.ChangeItemStatus(_, _, _, _) => "ChangeItemStatus"
            case ToDoListCommand.RenameToDoList(_, _, _)      => "RenameToDoList"
        }

    def commandUser(command: ToDoListCommand): String =
        match command {
            case ToDoListCommand.CreateToDoList(u, _)         => userName(u)
            case ToDoListCommand.AddToDoItem(u, _, _)         => userName(u)
            case ToDoListCommand.ChangeItemStatus(u, _, _, _) => userName(u)
            case ToDoListCommand.RenameToDoList(u, _, _)      => userName(u)
        }

    def commandList(command: ToDoListCommand): String =
        match command {
            case ToDoListCommand.CreateToDoList(_, l)         => listName(l)
            case ToDoListCommand.AddToDoItem(_, l, _)         => listName(l)
            case ToDoListCommand.ChangeItemStatus(_, l, _, _) => listName(l)
            case ToDoListCommand.RenameToDoList(_, l, _)      => listName(l)
        }

    /// コマンドの入口を包む。コマンドの種類・利用者・リストだけを載せる。項目の説明は載せない。
    pub def observeCommandHub(clock: Unit -> Int64 \ ec, hub: CommandHub[ef]): CommandHub[ec + ef + Logger] = {
        execute = command -> observe(clock, "command", commandName(command),
            Some(commandUser(command)), Some(commandList(command)), () -> hub#execute(command)),
        current = () -> observe(clock, "command", "current", None, None, () -> hub#current())
    }

    /// イベントの保管場所を包む。データベースの呼び出しが、1 回ごとに記録される。
    pub def observeStore(clock: Unit -> Int64 \ ec, store: EventStore[ef]): EventStore[ec + ef + Logger] = {
        load = () -> observe(clock, "store", "load", None, None, () -> store#load()),
        append = (expected, events) -> observe(clock, "store", "append", None, None, () -> store#append(expected, events))
    }

    def statusClass(status: Int32): String = Int32.toString(status / 100) + "xx"

    /// HTTP の要求を包む。メソッドと、状態コードの区分だけを載せる。
    /// 経路・クエリ・本文は、利用者の入力そのものなので載せない。
    pub def observeApp(clock: Unit -> Int64 \ ec, app: Web.Http.Request -> Web.Http.Response \ ef): Web.Http.Request -> Web.Http.Response \ ec + ef + Logger =
        request -> {
            let startedAt = clock();
            let response = app(request);
            let finishedAt = clock();
            let record = LogRecord.LogRecord("http", request#method, statusClass(response#status), None, None,
                Option.getWithDefault(Int32.maxValue(), Int64.tryToInt32(finishedAt - startedAt)));
            Logger.info(Json.render(Logging.Logs.toJson(record)));
            response
        }

}
