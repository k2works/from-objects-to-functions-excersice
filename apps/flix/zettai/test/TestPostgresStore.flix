mod TestPostgresStore {

    use Domain.{User, ListName, ToDoStatus}
    use Errors.ZettaiError
    use Events.ToDoListEvent
    use Events.ToDoListEvent.{ListCreated, ItemAdded, ItemStatusChanged}

    def uberto(): User = User.User("uberto")

    def book(): ListName = ListName.ListName("book")

    def history(): List[ToDoListEvent] = List#{
        ListCreated(uberto(), book()),
        ItemAdded(uberto(), book(), Domain.todo("write chapter & <test>")),
        ItemStatusChanged(uberto(), book(), "write chapter & <test>", ToDoStatus.Done)
    }

    /// テストごとに専用のテーブルを使い、終わったら捨てる。
    def withTable(f: (PostgresStore.Config, String) -> Unit \ {Assert, IO}): Unit \ {Assert, IO} = {
        let config = PostgresStore.configFromEnv();
        let table = PostgresStore.freshTableName();
        f(config, table);
        let _ = PostgresStore.dropTable(config, table);
        ()
    }

    @Test
    def aTableNameThatIsNotPlainIsRejectedBeforeAnyQuery(): Unit \ {Assert, IO} = {
        let config = PostgresStore.configFromEnv();
        match PostgresStore.open(config, "events; drop table users") {
            case Err(ZettaiError.StorageFailure(_)) => ()
            case _                                  => Assert.assertTrue(false)
        }
    }

    @Test
    def eventsComeBackInTheOrderTheyWereAppended(): Unit \ {Assert, IO} =
        withTable((config, table) -> match PostgresStore.open(config, table) {
            case Err(e)    => Assert.assertEq(expected = "opened", ToString.toString(e))
            case Ok(store) => {
                Assert.assertEq(expected = Ok(()), store#append(0, history()));
                Assert.assertEq(expected = Ok(history()), Result.map(read -> read#events, store#load()))
            }
        })

    @Test
    def anotherConnectionReadsWhatWasWritten(): Unit \ {Assert, IO} =
        withTable((config, table) -> match (PostgresStore.open(config, table), PostgresStore.open(config, table)) {
            case (Ok(writer), Ok(reader)) => {
                Assert.assertEq(expected = Ok(()), writer#append(0, List#{ListCreated(uberto(), book())}));
                Assert.assertEq(expected = Ok(List#{ListCreated(uberto(), book())}), Result.map(read -> read#events, reader#load()))
            }
            case _ => Assert.assertTrue(false)
        })

    @Test
    def aRowThatCannotBeReadIsAFailureNotAnException(): Unit \ {Assert, IO} =
        withTable((config, table) -> match PostgresStore.open(config, table) {
            case Err(_)    => Assert.assertTrue(false)
            case Ok(store) => {
                let inserted = PostgresStore.execute(config,
                    "insert into ${table} (version, kind, user_name, list_name, description, status) values (1, 'Deleted', 'u', 'l', '', '')");
                Assert.assertEq(expected = Ok(()), inserted);
                Assert.assertEq(expected = Err(ZettaiError.StorageFailure("unknown event kind: Deleted")), Result.map(read -> read#version, store#load()))
            }
        })

    @Test
    def anUnreachableDatabaseIsAFailureNotAnException(): Unit \ {Assert, IO} = {
        let config = { url = "jdbc:postgresql://localhost:1/zettai", user = "zettai", password = "zettai" };
        match PostgresStore.open(config, "events_unreachable") {
            case Err(ZettaiError.StorageFailure(_)) => ()
            case _                                  => Assert.assertTrue(false)
        }
    }

}
