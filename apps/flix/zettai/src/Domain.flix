mod Domain {

    pub enum User(String) with Eq, Order, ToString

    pub enum ListName(String) with Eq, Order, ToString

    pub enum ToDoStatus with Eq, ToString {
        case Todo, InProgress, Done, Blocked
    }

    pub enum ToDoItem(String, ToDoStatus) with Eq, ToString

    pub enum ToDoList(ListName, List[ToDoItem]) with Eq, ToString

    pub def todo(description: String): ToDoItem = ToDoItem.ToDoItem(description, ToDoStatus.Todo)

    pub def allStatuses(): List[ToDoStatus] =
        List#{ToDoStatus.Todo, ToDoStatus.InProgress, ToDoStatus.Done, ToDoStatus.Blocked}

    /// 許される状態遷移。Done からは戻せない。Todo から Blocked へは直接移せない。
    pub def canTransition(current: ToDoStatus, next: ToDoStatus): Bool =
        match (current, next) {
            case (ToDoStatus.Todo, ToDoStatus.InProgress)       => true
            case (ToDoStatus.Todo, ToDoStatus.Done)             => true
            case (ToDoStatus.InProgress, ToDoStatus.Done)       => true
            case (ToDoStatus.InProgress, ToDoStatus.Blocked)    => true
            case (ToDoStatus.Blocked, ToDoStatus.InProgress)    => true
            case _                                              => false
        }

    /// 説明が最初に一致した項目だけの状態を変える。状態への適用と射影の両方がこの規則を共有する。
    pub def changeStatus(items: List[ToDoItem], description: String, status: ToDoStatus): List[ToDoItem] =
        match items {
            case Nil => Nil
            case ToDoItem.ToDoItem(d, _) :: rest if d == description => ToDoItem.ToDoItem(d, status) :: rest
            case item :: rest => item :: changeStatus(rest, description, status)
        }

    pub def findToDoList(lists: Map[User, List[ToDoList]], user: User, name: ListName): Option[ToDoList] =
        Map.get(user, lists) |> Option.flatMap(userLists ->
            List.find(l -> match l { case ToDoList.ToDoList(n, _) => n == name }, userLists))

}
