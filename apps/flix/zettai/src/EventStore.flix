mod EventStore {

    use Errors.ZettaiError
    use Events.ToDoListEvent

    /// 追記だけができるイベントの保管場所。効果 ef は使う側が決める。
    pub type alias EventStore[ef: Eff] = {
        load = Unit -> Result[ZettaiError, List[ToDoListEvent]] \ ef,
        append = List[ToDoListEvent] -> Result[ZettaiError, Unit] \ ef
    }

    /// 領域 rc の中にだけ存在するストア。領域の外からは見えない。
    pub def inMemory(rc: Region[r]): EventStore[r] \ r = {
        let history = Ref.fresh(rc, Nil);
        {
            load = () -> Ok(Ref.get(history)),
            append = events -> { Ref.put(Ref.get(history) ::: events, history); Ok(()) }
        }
    }

}
