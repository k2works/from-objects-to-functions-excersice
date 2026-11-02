mod ZettaiActions {

    use Domain.{User, ListName, ToDoItem}

    /// シナリオが使う操作。どの経路で実行されるかをシナリオは知らない。
    pub type alias ZettaiActions = {
        name = String,
        getToDoList = (User, ListName) -> Option[List[ToDoItem]] \ IO,
        close = Unit -> Unit \ IO
    }

}
