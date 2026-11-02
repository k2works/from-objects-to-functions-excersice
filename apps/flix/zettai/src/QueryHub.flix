mod QueryHub {

    use Domain.User
    use Errors.ZettaiError
    use EventStore.EventStore
    use Projection.ToDoListRow

    /// 読み取り専用の入口。コマンドを実行する操作を、型として持たない。
    pub type alias QueryHub[ef: Eff] = {
        listRows = User -> Result[ZettaiError, List[ToDoListRow]] \ ef
    }

    pub def queryHub(store: EventStore[ef]): QueryHub[ef] = {
        listRows = user -> Result.map(history -> Projection.projectFrom(user, history), store#load())
    }

}
