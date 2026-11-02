mod Lookup {

    use Domain.{User, ListName, ToDoList}
    use Errors.ZettaiError

    /// 依存を「効果」として宣言する。実装は呼び出し側のハンドラが与える。
    pub eff ToDoLists {
        def getList(user: User, name: ListName): Result[ZettaiError, ToDoList]
    }

    pub def handleInMemory(lists: Map[User, List[ToDoList]], f: Unit -> a \ ef): a \ (ef - ToDoLists) =
        run {
            f()
        } with handler ToDoLists {
            def getList(user, name, resume) = resume(match Domain.findToDoList(lists, user, name) {
                case Some(list) => Ok(list)
                case None       => Err(ZettaiError.ListNotFound(user, name))
            })
        }

}
