mod QueryHub {

    use Domain.User
    use EventStore.EventStore
    use Projection.ToDoListRow

    /// 読み取り専用の入口。コマンドを実行する操作を、型として持たない。
    pub type alias QueryHub[ef: Eff] = {
        listRows = User -> List[ToDoListRow] \ ef
    }

    pub def queryHub(store: EventStore[ef]): QueryHub[ef] = {
        listRows = user -> Projection.projectFrom(user, store#load())
    }

}
