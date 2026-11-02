pub mod Application.Hub {

    use Domain.{User, ListName, ToDoList}
    use Domain.Errors.ZettaiError
    use Application.EventStore.EventStore

    /// アプリケーションが公開する操作。各操作は関数値のフィールドで、効果 ef を引き継ぐ。
    pub type alias ToDoListHub[ef: Eff] = {
        getList = (User, ListName) -> Result[ZettaiError, ToDoList] \ ef
    }

    pub def todoListHub(fetch: (User, ListName) -> Result[ZettaiError, ToDoList] \ ef): ToDoListHub[ef] =
        { getList = fetch }

    /// イベントストアの上に作る、読み取りの入口。毎回、履歴を読んで状態を作る。
    pub def fromStore(store: EventStore[ef]): ToDoListHub[ef] =
        todoListHub((user, name) -> match store#load() {
            case Err(e)   => Err(e)
            case Ok(read) =>
                match Domain.Events.listFor(Domain.Events.Replay.replayFrom(Domain.Events.empty(), read#events), user, name) {
                    case Some(list) => Ok(list)
                    case None       => Err(ZettaiError.ListNotFound(user, name))
                }
        })

    pub def inMemory(lists: Map[User, List[ToDoList]]): ToDoListHub[{}] =
        todoListHub((user, name) -> match Domain.findToDoList(lists, user, name) {
            case Some(list) => Ok(list)
            case None       => Err(ZettaiError.ListNotFound(user, name))
        })

}
