mod CommandHub {

    use Commands.ToDoListCommand
    use Errors.ZettaiError
    use EventStore.EventStore
    use Events.ToDoListState

    /// コマンドを受け付ける入り口。Hub の読み取り側と対になる。
    pub type alias CommandHub[ef: Eff] = {
        execute = ToDoListCommand -> Result[ZettaiError, Unit] \ ef,
        current = Unit -> Result[ZettaiError, ToDoListState] \ ef
    }

    pub def commandHub(store: EventStore[ef]): CommandHub[ef] = {
        execute = command -> forM (
            history <- store#load();
            events  <- Commands.handle(command, Replay.replayFrom(Events.empty(), history));
            _       <- store#append(events)
        ) yield (),
        current = () -> Result.map(history -> Replay.replayFrom(Events.empty(), history), store#load())
    }

}
