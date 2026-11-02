mod TestQueryHub {

    use Domain.{User, ListName, ToDoStatus}
    use Commands.ToDoListCommand.{CreateToDoList, AddToDoItem, ChangeItemStatus}
    use Projection.ToDoListRow

    def uberto(): User = User.User("uberto")

    def book(): ListName = ListName.ListName("book")

    /// コマンド側で書き、クエリ側で読む。2 つは同じストアを別々の入口から見る。
    def rowsAfter(commands: List[Commands.ToDoListCommand]): List[ToDoListRow] = {
        region rc {
            let store = EventStore.inMemory(rc);
            let commandHub = CommandHub.commandHub(store);
            let queryHub = QueryHub.queryHub(store);
            List.forEach(c -> { let _ = commandHub#execute(c); () }, commands);
            queryHub#listRows(uberto())
        }
    }

    @Test
    def theQuerySideSeesWhatTheCommandSideWrote(): Unit \ Assert =
        Assert.assertEq(
            expected = List#{ToDoListRow.ToDoListRow(book(), 2, 1)},
            rowsAfter(List#{
                CreateToDoList(uberto(), book()),
                AddToDoItem(uberto(), book(), Domain.todo("a")),
                AddToDoItem(uberto(), book(), Domain.todo("b")),
                ChangeItemStatus(uberto(), book(), "a", ToDoStatus.Done)
            })
        )

    @Test
    def aRejectedCommandDoesNotShowUpInTheRows(): Unit \ Assert =
        Assert.assertEq(
            expected = List#{ToDoListRow.ToDoListRow(book(), 0, 0)},
            rowsAfter(List#{CreateToDoList(uberto(), book()), CreateToDoList(uberto(), book())})
        )

    @Test
    def aUserWithoutListsHasNoRows(): Unit \ Assert =
        Assert.assertEq(expected = Nil, rowsAfter(Nil))

}
