mod Lookup {

    use Domain.{User, ListName, ToDoList}

    /// 依存を「効果」として宣言する。実装は呼び出し側のハンドラが与える。
    pub eff ToDoLists {
        def getList(user: User, name: ListName): Option[ToDoList]
    }

    pub def handleInMemory(lists: Map[User, List[ToDoList]], f: Unit -> a \ ef): a \ (ef - ToDoLists) =
        run {
            f()
        } with handler ToDoLists {
            def getList(user, name, resume) = resume(Domain.findToDoList(lists, user, name))
        }

}
