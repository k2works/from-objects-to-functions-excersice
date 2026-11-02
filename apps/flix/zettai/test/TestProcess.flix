mod TestProcess {

    use Domain.{User, ListName, ToDoStatus}
    use Domain.Errors.ZettaiError
    use Domain.Commands.ToDoListCommand
    use Domain.Commands.ToDoListCommand.{CreateToDoList, AddToDoItem, ChangeItemStatus}

    def uberto(): User = User.User("uberto")

    def book(): ListName = ListName.ListName("book")

    def commands(): List[ToDoListCommand] = List#{
        CreateToDoList(uberto(), book()),
        AddToDoItem(uberto(), book(), Domain.todo("a")),
        ChangeItemStatus(uberto(), book(), "a", ToDoStatus.InProgress),
        ChangeItemStatus(uberto(), book(), "a", ToDoStatus.Done),
        ChangeItemStatus(uberto(), book(), "a", ToDoStatus.Blocked)
    }

    def ofLength(k: Int32): List[List[ToDoListCommand]] =
        if (k <= 0)
            Nil :: Nil
        else
            List.flatMap(c -> List.map(rest -> c :: rest, ofLength(k - 1)), commands())

    def allSequences(): List[List[ToDoListCommand]] =
        List.flatMap(ofLength, List.range(0, 4))

    /// 文脈を引数で渡す版（CommandHub）。
    def byHub(sequence: List[ToDoListCommand]): List[Result[ZettaiError, Unit]] = {
        region rc {
            let hub = Application.CommandHub.commandHub(Application.EventStore.inMemory(rc));
            List.map(hub#execute, sequence)
        }
    }

    /// 文脈を効果ハンドラで渡す版（Process）。
    def byHandler(sequence: List[ToDoListCommand]): List[Result[ZettaiError, Unit]] = {
        region rc {
            Application.Process.withStore(Application.EventStore.inMemory(rc), () -> List.map(Application.Process.process, sequence))
        }
    }

    @Test
    def bothWaysOfCarryingTheContextGiveTheSameResults(): Unit \ Assert =
        List.forEach(sequence -> Assert.assertEq(expected = byHub(sequence), byHandler(sequence)), allSequences())

    @Test
    def aTransactionCommitsWhatEveryCommandInItWrote(): Unit \ {Assert, IO} = {
        let config = Adapter.Postgres.PostgresStore.configFromEnv();
        let table = Adapter.Postgres.PostgresStore.freshTableName();
        let _ = Adapter.Postgres.PostgresStore.open(config, table);
        let outcome = Adapter.Postgres.PostgresStore.transactional(config, table, () -> forM (
            _ <- Application.Process.process(CreateToDoList(uberto(), book()));
            _ <- Application.Process.process(AddToDoItem(uberto(), book(), Domain.todo("a")))
        ) yield ());
        Assert.assertEq(expected = Ok(()), outcome);
        match Adapter.Postgres.PostgresStore.open(config, table) {
            case Ok(store) => Assert.assertEq(expected = Ok(2), Result.map(read -> read#version, store#load()))
            case Err(e)    => Assert.assertEq(expected = "opened", ToString.toString(e))
        };
        let _ = Adapter.Postgres.PostgresStore.dropTable(config, table);
        ()
    }

    @Test
    def aRejectedCommandRollsBackTheCommandsBeforeItInTheSameTransaction(): Unit \ {Assert, IO} = {
        let config = Adapter.Postgres.PostgresStore.configFromEnv();
        let table = Adapter.Postgres.PostgresStore.freshTableName();
        let _ = Adapter.Postgres.PostgresStore.open(config, table);
        let outcome = Adapter.Postgres.PostgresStore.transactional(config, table, () -> forM (
            _ <- Application.Process.process(CreateToDoList(uberto(), book()));
            _ <- Application.Process.process(CreateToDoList(uberto(), book()))
        ) yield ());
        Assert.assertEq(expected = Err(ZettaiError.ListAlreadyExists(uberto(), book())), outcome);
        match Adapter.Postgres.PostgresStore.open(config, table) {
            case Ok(store) => Assert.assertEq(expected = Ok(0), Result.map(read -> read#version, store#load()))
            case Err(e)    => Assert.assertEq(expected = "opened", ToString.toString(e))
        };
        let _ = Adapter.Postgres.PostgresStore.dropTable(config, table);
        ()
    }

}
