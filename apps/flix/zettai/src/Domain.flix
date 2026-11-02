mod Domain {

    pub enum User(String) with Eq, Order, ToString

    pub enum ListName(String) with Eq, Order, ToString

    pub enum ToDoItem(String) with Eq, ToString

    pub enum ToDoList(ListName, List[ToDoItem]) with Eq, ToString

    pub def findToDoList(lists: Map[User, List[ToDoList]], user: User, name: ListName): Option[ToDoList] =
        Map.get(user, lists) |> Option.flatMap(userLists ->
            List.find(l -> match l { case ToDoList.ToDoList(n, _) => n == name }, userLists))

}
