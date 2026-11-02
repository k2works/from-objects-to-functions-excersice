mod TestEvents {

    use Domain.{User, ListName, ToDoList}
    use Domain.Events.ToDoListEvent.{ListCreated, ItemAdded}
    use Domain.Events.ToDoListState

    def uberto(): User = User.User("uberto")

    def book(): ListName = ListName.ListName("book")

    @Test
    def aCreatedListStartsEmpty(): Unit \ Assert =
        Assert.assertEq(
            expected = Some(ToDoList.ToDoList(book(), Nil)),
            Domain.Events.listFor(Domain.Events.apply(Domain.Events.empty(), ListCreated(uberto(), book())), uberto(), book())
        )

    @Test
    def anAddedItemAppearsInTheList(): Unit \ Assert = {
        let created = Domain.Events.apply(Domain.Events.empty(), ListCreated(uberto(), book()));
        let added = Domain.Events.apply(created, ItemAdded(uberto(), book(), Domain.todo("write chapter")));
        Assert.assertEq(
            expected = Some(ToDoList.ToDoList(book(), List#{Domain.todo("write chapter")})),
            Domain.Events.listFor(added, uberto(), book())
        )
    }

    @Test
    def applyingAnEventDoesNotChangeThePreviousState(): Unit \ Assert = {
        let empty = Domain.Events.empty();
        let created = Domain.Events.apply(empty, ListCreated(uberto(), book()));
        let added = Domain.Events.apply(created, ItemAdded(uberto(), book(), Domain.todo("write chapter")));
        Assert.assertEq(expected = None, Domain.Events.listFor(empty, uberto(), book()));
        Assert.assertEq(expected = Some(ToDoList.ToDoList(book(), Nil)), Domain.Events.listFor(created, uberto(), book()));
        Assert.assertEq(expected = Some(ToDoList.ToDoList(book(), List#{Domain.todo("write chapter")})), Domain.Events.listFor(added, uberto(), book()))
    }

    @Test
    def anItemAddedToAMissingListIsIgnored(): Unit \ Assert = {
        let state = Domain.Events.apply(Domain.Events.empty(), ItemAdded(uberto(), book(), Domain.todo("write chapter")));
        Assert.assertEq(expected = None, Domain.Events.listFor(state, uberto(), book()))
    }

    @Test
    def itemsKeepTheOrderTheyWereAdded(): Unit \ Assert = {
        let events = List#{
            ListCreated(uberto(), book()),
            ItemAdded(uberto(), book(), Domain.todo("a")),
            ItemAdded(uberto(), book(), Domain.todo("b"))
        };
        let state = List.foldLeft(Domain.Events.apply, Domain.Events.empty(), events);
        Assert.assertEq(
            expected = Some(ToDoList.ToDoList(book(), List#{Domain.todo("a"), Domain.todo("b")})),
            Domain.Events.listFor(state, uberto(), book())
        )
    }

    @Test
    def aStatusChangeAppliesOnlyToTheFirstItemWithThatDescription(): Unit \ Assert = {
        use Domain.Events.ToDoListEvent.ItemStatusChanged;
        let events = List#{
            ListCreated(uberto(), book()),
            ItemAdded(uberto(), book(), Domain.todo("a")),
            ItemAdded(uberto(), book(), Domain.todo("a")),
            ItemStatusChanged(uberto(), book(), "a", Domain.ToDoStatus.Done)
        };
        let state = List.foldLeft(Domain.Events.apply, Domain.Events.empty(), events);
        Assert.assertEq(
            expected = Some(ToDoList.ToDoList(book(), List#{
                Domain.ToDoItem.ToDoItem("a", Domain.ToDoStatus.Done),
                Domain.todo("a")
            })),
            Domain.Events.listFor(state, uberto(), book())
        )
    }

}
