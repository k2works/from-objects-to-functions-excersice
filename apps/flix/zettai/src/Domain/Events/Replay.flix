pub mod Domain.Events.Replay {

    use Domain.Events.ToDoListEvent
    use Domain.Events.ToDoListState

    /// 学習用。標準関数を使わず、再帰の形をそのまま見せる。
    pub def replayByRecursion(initial: ToDoListState, events: List[ToDoListEvent]): ToDoListState =
        match events {
            case Nil     => initial
            case e :: es => replayByRecursion(Domain.Events.apply(initial, e), es)
        }

    /// イベント列を、左から順に状態へ畳み込む。
    pub def replayFrom(initial: ToDoListState, events: List[ToDoListEvent]): ToDoListState =
        List.foldLeft(Domain.Events.apply, initial, events)

}
