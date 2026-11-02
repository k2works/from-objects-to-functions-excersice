mod Replay {

    use Events.ToDoListEvent
    use Events.ToDoListState

    /// 学習用。標準関数を使わず、再帰の形をそのまま見せる。
    pub def replayByRecursion(initial: ToDoListState, events: List[ToDoListEvent]): ToDoListState =
        match events {
            case Nil     => initial
            case e :: es => replayByRecursion(Events.apply(initial, e), es)
        }

    /// イベント列を、左から順に状態へ畳み込む。
    pub def replayFrom(initial: ToDoListState, events: List[ToDoListEvent]): ToDoListState =
        List.foldLeft(Events.apply, initial, events)

}
