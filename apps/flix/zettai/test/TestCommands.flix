mod TestCommands {

    use Domain.{User, ListName, ToDoList, ToDoStatus}
    use Errors.ZettaiError
    use Events.ToDoListEvent.{ListCreated, ItemAdded, ItemStatusChanged}
    use Commands.ToDoListCommand.{CreateToDoList, AddToDoItem, ChangeItemStatus}

    def uberto(): User = User.User("uberto")

    def book(): ListName = ListName.ListName("book")

    /// book リストに "write chapter" がある状態
    def withItem(): Events.ToDoListState =
        Replay.replayFrom(Events.empty(), List#{
            ListCreated(uberto(), book()),
            ItemAdded(uberto(), book(), Domain.todo("write chapter"))
        })

    @Test
    def createsANewList(): Unit \ Assert =
        Assert.assertEq(
            expected = Ok(List#{ListCreated(uberto(), book())}),
            Commands.handle(CreateToDoList(uberto(), book()), Events.empty())
        )

    @Test
    def rejectsCreatingAnExistingList(): Unit \ Assert =
        Assert.assertEq(
            expected = Err(ZettaiError.ListAlreadyExists(uberto(), book())),
            Commands.handle(CreateToDoList(uberto(), book()), withItem())
        )

    @Test
    def addsAnItemToAnExistingList(): Unit \ Assert =
        Assert.assertEq(
            expected = Ok(List#{ItemAdded(uberto(), book(), Domain.todo("read"))}),
            Commands.handle(AddToDoItem(uberto(), book(), Domain.todo("read")), withItem())
        )

    @Test
    def rejectsAddingAnItemToAMissingList(): Unit \ Assert =
        Assert.assertEq(
            expected = Err(ZettaiError.ListNotFound(uberto(), book())),
            Commands.handle(AddToDoItem(uberto(), book(), Domain.todo("read")), Events.empty())
        )

    @Test
    def changesTheStatusOfAnExistingItem(): Unit \ Assert =
        Assert.assertEq(
            expected = Ok(List#{ItemStatusChanged(uberto(), book(), "write chapter", ToDoStatus.InProgress)}),
            Commands.handle(ChangeItemStatus(uberto(), book(), "write chapter", ToDoStatus.InProgress), withItem())
        )

    @Test
    def rejectsChangingTheStatusOfAMissingItem(): Unit \ Assert =
        Assert.assertEq(
            expected = Err(ZettaiError.ItemNotFound(uberto(), book(), "nothing")),
            Commands.handle(ChangeItemStatus(uberto(), book(), "nothing", ToDoStatus.Done), withItem())
        )

    @Test
    def rejectsChangingTheStatusInAMissingList(): Unit \ Assert =
        Assert.assertEq(
            expected = Err(ZettaiError.ListNotFound(uberto(), book())),
            Commands.handle(ChangeItemStatus(uberto(), book(), "write chapter", ToDoStatus.Done), Events.empty())
        )

    @Test
    def rejectsATransitionTheTableDoesNotAllow(): Unit \ Assert =
        Assert.assertEq(
            expected = Err(ZettaiError.InvalidTransition("write chapter", ToDoStatus.Todo, ToDoStatus.Blocked)),
            Commands.handle(ChangeItemStatus(uberto(), book(), "write chapter", ToDoStatus.Blocked), withItem())
        )

    @Test
    def aStatusChangeIsAppliedToTheItemOnly(): Unit \ Assert = {
        let before = withItem();
        let after = Events.apply(before, ItemStatusChanged(uberto(), book(), "write chapter", ToDoStatus.Done));
        Assert.assertEq(
            expected = Some(ToDoList.ToDoList(book(), List#{Domain.ToDoItem.ToDoItem("write chapter", ToDoStatus.Done)})),
            Events.listFor(after, uberto(), book())
        );
        Assert.assertEq(
            expected = Some(ToDoList.ToDoList(book(), List#{Domain.todo("write chapter")})),
            Events.listFor(before, uberto(), book())
        )
    }

}
