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

    pub def findToDoList(lists: Map[User, List[ToDoList]], user: User, name: ListName): Option[ToDoList] =
        Map.get(user, lists) |> Option.flatMap(userLists ->
            List.find(l -> match l { case ToDoList.ToDoList(n, _) => n == name }, userLists))

}
