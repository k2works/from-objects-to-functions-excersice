mod TestQueryOnDatabase {

    use Domain.{User, ListName, ToDoStatus}
    use Domain.Commands.ToDoListCommand.{CreateToDoList, AddToDoItem, ChangeItemStatus}
    use Domain.Queries.Projection.ToDoListRow

    def uberto(): User = User.User("uberto")

    def book(): ListName = ListName.ListName("book")

    @Test
    def theQuerySideReadsRowsFromEventsStoredInPostgres(): Unit \ {Assert, IO} = {
        let config = Adapter.Postgres.PostgresStore.configFromEnv();
        let table = Adapter.Postgres.PostgresStore.freshTableName();
        match Adapter.Postgres.PostgresStore.open(config, table) {
            case Err(e)    => Assert.assertEq(expected = "opened", ToString.toString(e))
            case Ok(store) => {
                let commandHub = Application.CommandHub.commandHub(store);
                let queryHub = Application.QueryHub.queryHub(store);
                List.forEach(c -> { let _ = commandHub#execute(c); () }, List#{
                    CreateToDoList(uberto(), book()),
                    AddToDoItem(uberto(), book(), Domain.todo("a")),
                    AddToDoItem(uberto(), book(), Domain.todo("b")),
                    ChangeItemStatus(uberto(), book(), "a", ToDoStatus.Done)
                });
                Assert.assertEq(
                    expected = Ok(List#{ToDoListRow.ToDoListRow(book(), 2, 1)}),
                    queryHub#listRows(uberto())
                )
            }
        };
        let _ = Adapter.Postgres.PostgresStore.dropTable(config, table);
        ()
    }

}
