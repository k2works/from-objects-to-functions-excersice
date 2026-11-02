mod Hub {

    use Domain.{User, ListName, ToDoList}
    use Errors.ZettaiError

    /// アプリケーションが公開する操作。各操作は関数値のフィールドで、効果 ef を引き継ぐ。
    pub type alias ToDoListHub[ef: Eff] = {
        getList = (User, ListName) -> Result[ZettaiError, ToDoList] \ ef
    }

    pub def todoListHub(fetch: (User, ListName) -> Result[ZettaiError, ToDoList] \ ef): ToDoListHub[ef] =
        { getList = fetch }

    pub def inMemory(lists: Map[User, List[ToDoList]]): ToDoListHub[{}] =
        todoListHub((user, name) -> match Domain.findToDoList(lists, user, name) {
            case Some(list) => Ok(list)
            case None       => Err(ZettaiError.ListNotFound(user, name))
        })

}
