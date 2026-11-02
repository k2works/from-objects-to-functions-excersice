pub mod Application.EventStore {

    use Domain.Errors.ZettaiError
    use Domain.Events.ToDoListEvent

    /// 読んだ時点のイベントと、その版。版は、これまでに書かれたイベントの数。
    pub type alias Versioned = { events = List[ToDoListEvent], version = Int32 }

    /// 追記だけができるイベントの保管場所。効果 ef は使う側が決める。
    /// 書くときは、読んだ版を添える。その後に誰かが書いていたら、書かずに断る。
    pub type alias EventStore[ef: Eff] = {
        load = Unit -> Result[ZettaiError, Versioned] \ ef,
        append = (Int32, List[ToDoListEvent]) -> Result[ZettaiError, Unit] \ ef
    }

    /// 領域 rc の中にだけ存在するストア。領域の外からは見えない。
    pub def inMemory(rc: Region[r]): EventStore[r] \ r = {
        let history = Ref.fresh(rc, Nil);
        {
            load = () -> {
                let events = Ref.get(history);
                Ok({ events = events, version = List.length(events) })
            },
            append = (expected, events) -> {
                let current = Ref.get(history);
                let actual = List.length(current);
                if (expected != actual) {
                    Err(ZettaiError.VersionConflict(expected, actual))
                } else {
                    Ref.put(current ::: events, history);
                    Ok(())
                }
            }
        }
    }

}
