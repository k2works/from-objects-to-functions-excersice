pub mod Domain.Events {

    use Domain.{User, ListName, ToDoItem, ToDoList, ToDoStatus}

    /// すでに起きた出来事。命令ではないので過去形で名付ける。
    pub enum ToDoListEvent with Eq, ToString {
        case ListCreated(User, ListName)
        case ItemAdded(User, ListName, ToDoItem)
        case ItemStatusChanged(User, ListName, String, ToDoStatus)
        case ListRenamed(User, ListName, ListName)
    }

    /// イベントから導出される現在の状態。
    pub enum ToDoListState(Map[(User, ListName), ToDoList]) with Eq, ToString

    pub def empty(): ToDoListState = ToDoListState.ToDoListState(Map#{})

    pub def listFor(state: ToDoListState, user: User, name: ListName): Option[ToDoList] =
        match state { case ToDoListState.ToDoListState(lists) => Map.get((user, name), lists) }

    def put(state: ToDoListState, user: User, name: ListName, list: ToDoList): ToDoListState =
        match state {
            case ToDoListState.ToDoListState(lists) =>
                ToDoListState.ToDoListState(Map.insert((user, name), list, lists))
        }

    def remove(state: ToDoListState, user: User, name: ListName): ToDoListState =
        match state {
            case ToDoListState.ToDoListState(lists) =>
                ToDoListState.ToDoListState(Map.remove((user, name), lists))
        }

    /// 1 つのイベントを適用し、新しい状態を返す。元の状態は変わらない。
    pub def apply(state: ToDoListState, event: ToDoListEvent): ToDoListState =
        match event {
            case ToDoListEvent.ListCreated(user, name) =>
                put(state, user, name, ToDoList.ToDoList(name, Nil))
            case ToDoListEvent.ItemAdded(user, name, item) =>
                match listFor(state, user, name) {
                    case Some(ToDoList.ToDoList(_, items)) =>
                        put(state, user, name, ToDoList.ToDoList(name, items ::: item :: Nil))
                    case None => state
                }
            case ToDoListEvent.ListRenamed(user, name, _) => remove(state, user, name)
            case ToDoListEvent.ItemStatusChanged(user, name, description, status) =>
                match listFor(state, user, name) {
                    case Some(ToDoList.ToDoList(_, items)) =>
                        put(state, user, name, ToDoList.ToDoList(name, Domain.changeStatus(items, description, status)))
                    case None => state
                }
        }

}
