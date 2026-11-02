mod Errors {

    use Domain.{User, ListName, ToDoStatus}

    /// 業務上ありうる失敗の集合。文字列ではなく、判断に使った値を持つ。
    pub enum ZettaiError with Eq, ToString {
        case ListNotFound(User, ListName)
        case ListAlreadyExists(User, ListName)
        case ItemNotFound(User, ListName, String)
        case InvalidTransition(String, ToDoStatus, ToDoStatus)
        case StorageFailure(String)
        case VersionConflict(Int32, Int32)
    }

}
