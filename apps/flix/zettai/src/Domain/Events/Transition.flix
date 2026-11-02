pub mod Domain.Events.Transition {

    use Domain.Events.ToDoListEvent
    use Domain.Events.ToDoListState

    /// 状態を受け取り、状態を返す関数。イベントを 1 つ固定すると、この形になる。
    pub enum StateTransition(ToDoListState -> ToDoListState)

    pub def applyTo(transition: StateTransition, state: ToDoListState): ToDoListState =
        match transition { case StateTransition.StateTransition(f) => f(state) }

    pub def fromEvent(event: ToDoListEvent): StateTransition =
        StateTransition.StateTransition(state -> Domain.Events.apply(state, event))

    /// 何もしない変換。合成の単位元になる。
    pub def identity(): StateTransition =
        StateTransition.StateTransition(state -> state)

    /// 順番につなぐ。a の後に b を行う。
    pub def andThen(a: StateTransition, b: StateTransition): StateTransition =
        StateTransition.StateTransition(state -> applyTo(b, applyTo(a, state)))

    instance SemiGroup[StateTransition] {
        pub def combine(a: StateTransition, b: StateTransition): StateTransition = andThen(a, b)
    }

    instance Monoid[StateTransition] {
        pub def empty(): StateTransition = identity()
    }

    /// イベント列を、1 つの変換にまとめる。標準の Monoid.fold を使う。
    pub def fromEvents(events: List[ToDoListEvent]): StateTransition =
        Monoid.fold(List.map(fromEvent, events))

}
