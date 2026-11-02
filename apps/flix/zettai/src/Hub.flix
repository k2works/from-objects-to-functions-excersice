mod Hub {

    use Domain.{User, ListName, ToDoList}

    /// アプリケーションが公開する操作。各操作は関数値のフィールドで、効果 ef を引き継ぐ。
    pub type alias ToDoListHub[ef: Eff] = {
        getList = (User, ListName) -> Option[ToDoList] \ ef
    }

    pub def todoListHub(fetch: (User, ListName) -> Option[ToDoList] \ ef): ToDoListHub[ef] =
        { getList = fetch }

    pub def inMemory(lists: Map[User, List[ToDoList]]): ToDoListHub[{}] =
        todoListHub((user, name) -> Domain.findToDoList(lists, user, name))

}
