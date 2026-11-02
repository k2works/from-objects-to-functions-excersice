mod Errors {

    use Domain.{User, ListName, ToDoStatus}

    /// 利用者が入力する欄。
    pub enum InputField with Eq, ToString {
        case UserName
        case ListNameField
        case NewName
    }

    /// 入力の誤り。誤りのもとになった値は持たない（メッセージや記録に入力をそのまま出さないため）。
    pub enum FieldProblem with Eq, ToString {
        case Blank(InputField)
        case TooLong(InputField, Int32)
        case ForbiddenCharacter(InputField)
    }

    /// 業務上ありうる失敗の集合。文字列ではなく、判断に使った値を持つ。
    pub enum ZettaiError with Eq, ToString {
        case ListNotFound(User, ListName)
        case ListAlreadyExists(User, ListName)
        case ItemNotFound(User, ListName, String)
        case InvalidTransition(String, ToDoStatus, ToDoStatus)
        case StorageFailure(String)
        case VersionConflict(Int32, Int32)
        case InvalidInput(List[FieldProblem])
    }

}
