mod Commands {

    use Domain.{User, ListName, ToDoItem, ToDoList, ToDoStatus}
    use Errors.ZettaiError
    use Events.ToDoListEvent
    use Events.ToDoListState

    /// これから行いたい意図。イベントと違い、拒否されうる。
    pub enum ToDoListCommand {
        case CreateToDoList(User, ListName)
        case AddToDoItem(User, ListName, ToDoItem)
        case ChangeItemStatus(User, ListName, String, ToDoStatus)
    }

    def requireList(state: ToDoListState, user: User, name: ListName): Result[ZettaiError, ToDoList] =
        match Events.listFor(state, user, name) {
            case Some(list) => Ok(list)
            case None       => Err(ZettaiError.ListNotFound(user, name))
        }

    def requireItem(user: User, name: ListName, description: String, list: ToDoList): Result[ZettaiError, ToDoItem] =
        match list {
            case ToDoList.ToDoList(_, items) =>
                match List.find(item -> match item { case ToDoItem.ToDoItem(d, _) => d == description }, items) {
                    case Some(item) => Ok(item)
                    case None       => Err(ZettaiError.ItemNotFound(user, name, description))
                }
        }

    /// コマンドと現在の状態から、新しいイベントを決める純粋関数。
    pub def handle(command: ToDoListCommand, state: ToDoListState): Result[ZettaiError, List[ToDoListEvent]] =
        match command {
            case ToDoListCommand.CreateToDoList(user, name) =>
                match Events.listFor(state, user, name) {
                    case Some(_) => Err(ZettaiError.ListAlreadyExists(user, name))
                    case None    => Ok(ToDoListEvent.ListCreated(user, name) :: Nil)
                }
            case ToDoListCommand.AddToDoItem(user, name, item) =>
                forM (
                    _ <- requireList(state, user, name)
                ) yield ToDoListEvent.ItemAdded(user, name, item) :: Nil
            case ToDoListCommand.ChangeItemStatus(user, name, description, newStatus) =>
                forM (
                    list    <- requireList(state, user, name);
                    item    <- requireItem(user, name, description, list);
                    allowed <- checkTransition(description, item, newStatus)
                ) yield ToDoListEvent.ItemStatusChanged(user, name, allowed, newStatus) :: Nil
        }

    def checkTransition(description: String, item: ToDoItem, newStatus: ToDoStatus): Result[ZettaiError, String] =
        match item {
            case ToDoItem.ToDoItem(_, current) =>
                if (Domain.canTransition(current, newStatus))
                    Ok(description)
                else
                    Err(ZettaiError.InvalidTransition(description, current, newStatus))
        }

}
