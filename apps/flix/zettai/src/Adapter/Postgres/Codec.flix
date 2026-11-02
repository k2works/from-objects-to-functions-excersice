pub mod Adapter.Postgres.Codec {

    use Domain.{User, ListName, ToDoItem, ToDoStatus}
    use Domain.Errors.ZettaiError
    use Domain.Events.ToDoListEvent

    /// データベースの 1 行。型を持たない文字列だけで、外の世界との境界を表す。
    pub type alias EventRow = {
        kind = String,
        user = String,
        list = String,
        description = String,
        status = String
    }

    def statusName(status: ToDoStatus): String =
        match status {
            case ToDoStatus.Todo       => "Todo"
            case ToDoStatus.InProgress => "InProgress"
            case ToDoStatus.Done       => "Done"
            case ToDoStatus.Blocked    => "Blocked"
        }

    def parseStatus(name: String): Result[ZettaiError, ToDoStatus] =
        match name {
            case "Todo"       => Ok(ToDoStatus.Todo)
            case "InProgress" => Ok(ToDoStatus.InProgress)
            case "Done"       => Ok(ToDoStatus.Done)
            case "Blocked"    => Ok(ToDoStatus.Blocked)
            case other        => Err(ZettaiError.StorageFailure("unknown status: ${other}"))
        }

    pub def encode(event: ToDoListEvent): EventRow =
        match event {
            case ToDoListEvent.ListCreated(User.User(u), ListName.ListName(l)) =>
                { kind = "ListCreated", user = u, list = l, description = "", status = "" }
            case ToDoListEvent.ItemAdded(User.User(u), ListName.ListName(l), ToDoItem.ToDoItem(d, s)) =>
                { kind = "ItemAdded", user = u, list = l, description = d, status = statusName(s) }
            case ToDoListEvent.ItemStatusChanged(User.User(u), ListName.ListName(l), d, s) =>
                { kind = "ItemStatusChanged", user = u, list = l, description = d, status = statusName(s) }
            case ToDoListEvent.ListRenamed(User.User(u), ListName.ListName(l), ListName.ListName(n)) =>
                { kind = "ListRenamed", user = u, list = l, description = n, status = "" }
        }

    /// 行をイベントに戻す。読めない行は、例外ではなく値として失敗にする。
    pub def decode(row: EventRow): Result[ZettaiError, ToDoListEvent] = {
        let user = User.User(row#user);
        let list = ListName.ListName(row#list);
        match row#kind {
            case "ListCreated" => Ok(ToDoListEvent.ListCreated(user, list))
            case "ItemAdded" =>
                forM (s <- parseStatus(row#status))
                    yield ToDoListEvent.ItemAdded(user, list, ToDoItem.ToDoItem(row#description, s))
            case "ItemStatusChanged" =>
                forM (s <- parseStatus(row#status))
                    yield ToDoListEvent.ItemStatusChanged(user, list, row#description, s)
            case "ListRenamed" => Ok(ToDoListEvent.ListRenamed(user, list, ListName.ListName(row#description)))
            case other => Err(ZettaiError.StorageFailure("unknown event kind: ${other}"))
        }
    }

}
