pub mod Application.QueryHub {

    use Domain.User
    use Domain.Errors.ZettaiError
    use Application.EventStore.EventStore
    use Domain.Queries.Projection.ToDoListRow

    /// 読み取り専用の入口。コマンドを実行する操作を、型として持たない。
    pub type alias QueryHub[ef: Eff] = {
        listRows = User -> Result[ZettaiError, List[ToDoListRow]] \ ef
    }

    pub def queryHub(store: EventStore[ef]): QueryHub[ef] = {
        listRows = user -> Result.map(read -> Domain.Queries.Projection.projectFrom(user, read#events), store#load())
    }

}
