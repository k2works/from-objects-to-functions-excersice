mod TestVersionedStore {

    use Domain.{User, ListName}
    use Domain.Errors.ZettaiError
    use Application.EventStore.EventStore
    use Domain.Events.ToDoListEvent
    use Domain.Events.ToDoListEvent.{ListCreated, ItemAdded}

    def uberto(): User = User.User("uberto")

    def book(): ListName = ListName.ListName("book")

    def created(): ToDoListEvent = ListCreated(uberto(), book())

    def added(description: String): ToDoListEvent = ItemAdded(uberto(), book(), Domain.todo(description))

    /// どのストアも守る契約。読んだ版を添えて書き、古い版での書き込みは断る。
    def contract(store: EventStore[ef]): Unit \ {Assert, ef} = {
        let empty = store#load();
        Assert.assertEq(expected = Ok(0), Result.map(v -> v#version, empty));
        // 2 つのコマンドが、どちらも空の履歴を読んだ
        let first = store#append(0, created() :: Nil);
        let stale = store#append(0, created() :: Nil);
        Assert.assertEq(expected = Ok(()), first);
        Assert.assertEq(expected = Err(ZettaiError.VersionConflict(0, 1)), stale);
        // 断られた書き込みは、何も残さない
        Assert.assertEq(expected = Ok(created() :: Nil), Result.map(v -> v#events, store#load()));
        Assert.assertEq(expected = Ok(1), Result.map(v -> v#version, store#load()));
        // 読み直した版なら書ける。版はイベントの数だけ進む
        Assert.assertEq(expected = Ok(()), store#append(1, added("a") :: added("b") :: Nil));
        Assert.assertEq(expected = Ok(3), Result.map(v -> v#version, store#load()))
    }

    @Test
    def theInMemoryStoreKeepsTheContract(): Unit \ Assert = {
        region rc {
            contract(Application.EventStore.inMemory(rc))
        }
    }

    @Test
    def thePostgresStoreKeepsTheContract(): Unit \ {Assert, IO} = {
        let config = Adapter.Postgres.PostgresStore.configFromEnv();
        let table = Adapter.Postgres.PostgresStore.freshTableName();
        match Adapter.Postgres.PostgresStore.open(config, table) {
            case Ok(store) => contract(store)
            case Err(e)    => Assert.assertEq(expected = "opened", ToString.toString(e))
        };
        let _ = Adapter.Postgres.PostgresStore.dropTable(config, table);
        ()
    }

}
