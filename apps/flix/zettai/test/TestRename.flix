mod TestRename {

    use Domain.{User, ListName, ToDoList, ToDoStatus}
    use Errors.ZettaiError
    use Events.ToDoListEvent.{ListCreated, ItemAdded, ItemStatusChanged, ListRenamed}
    use Commands.ToDoListCommand.RenameToDoList
    use Projection.ToDoListRow

    def uberto(): User = User.User("uberto")

    def book(): ListName = ListName.ListName("book")

    def shop(): ListName = ListName.ListName("shop")

    /// book に "a"（完了）と "b"（未着手）がある状態
    def history(): List[Events.ToDoListEvent] = List#{
        ListCreated(uberto(), book()),
        ItemAdded(uberto(), book(), Domain.todo("a")),
        ItemAdded(uberto(), book(), Domain.todo("b")),
        ItemStatusChanged(uberto(), book(), "a", ToDoStatus.Done)
    }

    def state(): Events.ToDoListState = Replay.replayFrom(Events.empty(), history())

    @Test
    def renamingMovesTheListAndKeepsEveryItemWithItsStatus(): Unit \ Assert =
        Assert.assertEq(
            expected = Ok(List#{
                ListRenamed(uberto(), book(), shop()),
                ListCreated(uberto(), shop()),
                ItemAdded(uberto(), shop(), Domain.ToDoItem.ToDoItem("a", ToDoStatus.Done)),
                ItemAdded(uberto(), shop(), Domain.todo("b"))
            }),
            Commands.handle(RenameToDoList(uberto(), book(), shop()), state())
        )

    @Test
    def aRenamedListReplacesTheOldOne(): Unit \ Assert = {
        let events = history() ::: Result.getWithDefault(Nil, Commands.handle(RenameToDoList(uberto(), book(), shop()), state()));
        let after = Replay.replayFrom(Events.empty(), events);
        Assert.assertEq(expected = None, Events.listFor(after, uberto(), book()));
        Assert.assertEq(
            expected = Some(ToDoList.ToDoList(shop(), List#{Domain.ToDoItem.ToDoItem("a", ToDoStatus.Done), Domain.todo("b")})),
            Events.listFor(after, uberto(), shop())
        )
    }

    @Test
    def renamingAndRenamingBackDoesNotDuplicateItems(): Unit \ Assert = {
        let first = history() ::: Result.getWithDefault(Nil, Commands.handle(RenameToDoList(uberto(), book(), shop()), state()));
        let second = first ::: Result.getWithDefault(Nil,
            Commands.handle(RenameToDoList(uberto(), shop(), book()), Replay.replayFrom(Events.empty(), first)));
        Assert.assertEq(
            expected = Events.listFor(state(), uberto(), book()),
            Events.listFor(Replay.replayFrom(Events.empty(), second), uberto(), book())
        )
    }

    @Test
    def renamingAMissingListIsRefused(): Unit \ Assert =
        Assert.assertEq(
            expected = Err(ZettaiError.ListNotFound(uberto(), shop())),
            Commands.handle(RenameToDoList(uberto(), shop(), book()), state())
        )

    @Test
    def renamingToAnExistingNameIsRefused(): Unit \ Assert = {
        let both = Replay.replayFrom(state(), List#{ListCreated(uberto(), shop())});
        Assert.assertEq(
            expected = Err(ZettaiError.ListAlreadyExists(uberto(), shop())),
            Commands.handle(RenameToDoList(uberto(), book(), shop()), both)
        )
    }

    @Test
    def renamingToTheSameNameIsRefusedBecauseTheNameIsTaken(): Unit \ Assert =
        Assert.assertEq(
            expected = Err(ZettaiError.ListAlreadyExists(uberto(), book())),
            Commands.handle(RenameToDoList(uberto(), book(), book()), state())
        )

    @Test
    def theProjectionShowsOnlyTheNewName(): Unit \ Assert = {
        let events = history() ::: Result.getWithDefault(Nil, Commands.handle(RenameToDoList(uberto(), book(), shop()), state()));
        Assert.assertEq(expected = List#{ToDoListRow.ToDoListRow(shop(), 2, 1)}, Projection.projectFrom(uberto(), events))
    }

}
