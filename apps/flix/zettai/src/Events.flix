mod Events {

    use Domain.{User, ListName, ToDoItem, ToDoList, ToDoStatus}

    /// すでに起きた出来事。命令ではないので過去形で名付ける。
    pub enum ToDoListEvent with Eq, ToString {
        case ListCreated(User, ListName)
        case ItemAdded(User, ListName, ToDoItem)
        case ItemStatusChanged(User, ListName, String, ToDoStatus)
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

    def withStatus(description: String, status: ToDoStatus): ToDoItem -> ToDoItem =
        item -> match item {
            case ToDoItem.ToDoItem(d, _) if d == description => ToDoItem.ToDoItem(d, status)
            case other                                       => other
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
            case ToDoListEvent.ItemStatusChanged(user, name, description, status) =>
                match listFor(state, user, name) {
                    case Some(ToDoList.ToDoList(_, items)) =>
                        put(state, user, name, ToDoList.ToDoList(name, List.map(withStatus(description, status), items)))
                    case None => state
                }
        }

}
