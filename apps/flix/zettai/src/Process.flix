mod Process {

    use Errors.ZettaiError
    use EventStore.{EventStore, Versioned}
    use Events.ToDoListEvent

    /// コマンドの処理が必要とする文脈。実装は、呼び出し側のハンドラが与える。
    pub eff EventLog {
        def load(): Result[ZettaiError, Versioned]
        def append(expected: Int32, events: List[ToDoListEvent]): Result[ZettaiError, Unit]
    }

    /// 読む、判断する、書く。文脈は引数に現れず、型の効果にだけ現れる。
    pub def process(command: Commands.ToDoListCommand): Result[ZettaiError, Unit] \ EventLog =
        forM (
            read   <- EventLog.load();
            events <- Commands.handle(command, Replay.replayFrom(Events.empty(), read#events));
            _      <- EventLog.append(read#version, events)
        ) yield ()

    /// 既存のイベントストアを、文脈として与えるハンドラ。
    pub def withStore(store: EventStore[r], f: Unit -> a \ ef): a \ (ef - EventLog) + r =
        run {
            f()
        } with handler EventLog {
            def load(resume) = resume(store#load())
            def append(expected, events, resume) = resume(store#append(expected, events))
        }

}
