mod CommandHub {

    use Commands.ToDoListCommand
    use Errors.ZettaiError
    use EventStore.EventStore
    use Events.ToDoListState

    /// コマンドを受け付ける入り口。Hub の読み取り側と対になる。
    pub type alias CommandHub[ef: Eff] = {
        execute = ToDoListCommand -> Result[ZettaiError, Unit] \ ef,
        current = Unit -> ToDoListState \ ef
    }

    pub def commandHub(store: EventStore[ef]): CommandHub[ef] = {
        execute = command -> {
            let state = Replay.replayFrom(Events.empty(), store#load());
            match Commands.handle(command, state) {
                case Ok(events) => { store#append(events); Ok(()) }
                case Err(e)     => Err(e)
            }
        },
        current = () -> Replay.replayFrom(Events.empty(), store#load())
    }

}
