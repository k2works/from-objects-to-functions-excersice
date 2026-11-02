mod TestCommands {

    use Domain.{User, ListName, ToDoList, ToDoStatus}
    use Domain.Errors.ZettaiError
    use Domain.Events.ToDoListEvent.{ListCreated, ItemAdded, ItemStatusChanged}
    use Domain.Commands.ToDoListCommand.{CreateToDoList, AddToDoItem, ChangeItemStatus}

    def uberto(): User = User.User("uberto")

    def book(): ListName = ListName.ListName("book")

    /// book リストに "write chapter" がある状態
    def withItem(): Domain.Events.ToDoListState =
        Domain.Events.Replay.replayFrom(Domain.Events.empty(), List#{
            ListCreated(uberto(), book()),
            ItemAdded(uberto(), book(), Domain.todo("write chapter"))
        })

    @Test
    def createsANewList(): Unit \ Assert =
        Assert.assertEq(
            expected = Ok(List#{ListCreated(uberto(), book())}),
            Domain.Commands.handle(CreateToDoList(uberto(), book()), Domain.Events.empty())
        )

    @Test
    def rejectsCreatingAnExistingList(): Unit \ Assert =
        Assert.assertEq(
            expected = Err(ZettaiError.ListAlreadyExists(uberto(), book())),
            Domain.Commands.handle(CreateToDoList(uberto(), book()), withItem())
        )

    @Test
    def addsAnItemToAnExistingList(): Unit \ Assert =
        Assert.assertEq(
            expected = Ok(List#{ItemAdded(uberto(), book(), Domain.todo("read"))}),
            Domain.Commands.handle(AddToDoItem(uberto(), book(), Domain.todo("read")), withItem())
        )

    @Test
    def rejectsAddingAnItemToAMissingList(): Unit \ Assert =
        Assert.assertEq(
            expected = Err(ZettaiError.ListNotFound(uberto(), book())),
            Domain.Commands.handle(AddToDoItem(uberto(), book(), Domain.todo("read")), Domain.Events.empty())
        )

    @Test
    def changesTheStatusOfAnExistingItem(): Unit \ Assert =
        Assert.assertEq(
            expected = Ok(List#{ItemStatusChanged(uberto(), book(), "write chapter", ToDoStatus.InProgress)}),
            Domain.Commands.handle(ChangeItemStatus(uberto(), book(), "write chapter", ToDoStatus.InProgress), withItem())
        )

    @Test
    def rejectsChangingTheStatusOfAMissingItem(): Unit \ Assert =
        Assert.assertEq(
            expected = Err(ZettaiError.ItemNotFound(uberto(), book(), "nothing")),
            Domain.Commands.handle(ChangeItemStatus(uberto(), book(), "nothing", ToDoStatus.Done), withItem())
        )

    @Test
    def rejectsChangingTheStatusInAMissingList(): Unit \ Assert =
        Assert.assertEq(
            expected = Err(ZettaiError.ListNotFound(uberto(), book())),
            Domain.Commands.handle(ChangeItemStatus(uberto(), book(), "write chapter", ToDoStatus.Done), Domain.Events.empty())
        )

    @Test
    def rejectsATransitionTheTableDoesNotAllow(): Unit \ Assert =
        Assert.assertEq(
            expected = Err(ZettaiError.InvalidTransition("write chapter", ToDoStatus.Todo, ToDoStatus.Blocked)),
            Domain.Commands.handle(ChangeItemStatus(uberto(), book(), "write chapter", ToDoStatus.Blocked), withItem())
        )

    @Test
    def aStatusChangeIsAppliedToTheItemOnly(): Unit \ Assert = {
        let before = withItem();
        let after = Domain.Events.apply(before, ItemStatusChanged(uberto(), book(), "write chapter", ToDoStatus.Done));
        Assert.assertEq(
            expected = Some(ToDoList.ToDoList(book(), List#{Domain.ToDoItem.ToDoItem("write chapter", ToDoStatus.Done)})),
            Domain.Events.listFor(after, uberto(), book())
        );
        Assert.assertEq(
            expected = Some(ToDoList.ToDoList(book(), List#{Domain.todo("write chapter")})),
            Domain.Events.listFor(before, uberto(), book())
        )
    }

}
