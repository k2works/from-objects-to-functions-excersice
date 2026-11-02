mod Projection {

    use Domain.{User, ListName, ToDoItem, ToDoStatus}
    use Events.ToDoListEvent

    /// 画面に出したい形。業務の判断に使う状態とは別に持つ。
    pub enum ToDoListRow(ListName, Int32, Int32) with Eq, ToString

    /// リスト名から項目の列への対応。射影の途中経過。
    type alias Lists = Map[ListName, List[ToDoItem]]

    def step(lists: Lists, event: ToDoListEvent): Lists =
        match event {
            case ToDoListEvent.ListCreated(_, name) => Map.insert(name, Nil, lists)
            case ToDoListEvent.ItemAdded(_, name, item) =>
                match Map.get(name, lists) {
                    case Some(items) => Map.insert(name, items ::: item :: Nil, lists)
                    case None        => lists
                }
            case ToDoListEvent.ItemStatusChanged(_, name, description, status) =>
                match Map.get(name, lists) {
                    case Some(items) => Map.insert(name, Domain.changeStatus(items, description, status), lists)
                    case None        => lists
                }
        }

    def owner(event: ToDoListEvent): User =
        match event {
            case ToDoListEvent.ListCreated(user, _)            => user
            case ToDoListEvent.ItemAdded(user, _, _)           => user
            case ToDoListEvent.ItemStatusChanged(user, _, _, _) => user
        }

    def isDone(item: ToDoItem): Bool =
        match item { case ToDoItem.ToDoItem(_, status) => status == ToDoStatus.Done }

    def toRow(entry: (ListName, List[ToDoItem])): ToDoListRow =
        match entry {
            case (name, items) => ToDoListRow.ToDoListRow(name, List.length(items), List.count(isDone, items))
        }

    /// 状態を作るときと同じ畳み込みで、イベントを行へ写す。畳み込む先が違うだけ。
    pub def projectFrom(user: User, events: List[ToDoListEvent]): List[ToDoListRow] =
        events
            |> List.filter(e -> owner(e) == user)
            |> List.foldLeft(step, Map#{})
            |> Map.toList
            |> List.map(toRow)

}
