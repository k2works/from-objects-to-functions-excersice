mod TestEvents {

    use Domain.{User, ListName, ToDoList}
    use Events.ToDoListEvent.{ListCreated, ItemAdded}
    use Events.ToDoListState

    def uberto(): User = User.User("uberto")

    def book(): ListName = ListName.ListName("book")

    @Test
    def aCreatedListStartsEmpty(): Unit \ Assert =
        Assert.assertEq(
            expected = Some(ToDoList.ToDoList(book(), Nil)),
            Events.listFor(Events.apply(Events.empty(), ListCreated(uberto(), book())), uberto(), book())
        )

    @Test
    def anAddedItemAppearsInTheList(): Unit \ Assert = {
        let created = Events.apply(Events.empty(), ListCreated(uberto(), book()));
        let added = Events.apply(created, ItemAdded(uberto(), book(), Domain.todo("write chapter")));
        Assert.assertEq(
            expected = Some(ToDoList.ToDoList(book(), List#{Domain.todo("write chapter")})),
            Events.listFor(added, uberto(), book())
        )
    }

    @Test
    def applyingAnEventDoesNotChangeThePreviousState(): Unit \ Assert = {
        let empty = Events.empty();
        let created = Events.apply(empty, ListCreated(uberto(), book()));
        let added = Events.apply(created, ItemAdded(uberto(), book(), Domain.todo("write chapter")));
        Assert.assertEq(expected = None, Events.listFor(empty, uberto(), book()));
        Assert.assertEq(expected = Some(ToDoList.ToDoList(book(), Nil)), Events.listFor(created, uberto(), book()));
        Assert.assertEq(expected = Some(ToDoList.ToDoList(book(), List#{Domain.todo("write chapter")})), Events.listFor(added, uberto(), book()))
    }

    @Test
    def anItemAddedToAMissingListIsIgnored(): Unit \ Assert = {
        let state = Events.apply(Events.empty(), ItemAdded(uberto(), book(), Domain.todo("write chapter")));
        Assert.assertEq(expected = None, Events.listFor(state, uberto(), book()))
    }

    @Test
    def itemsKeepTheOrderTheyWereAdded(): Unit \ Assert = {
        let events = List#{
            ListCreated(uberto(), book()),
            ItemAdded(uberto(), book(), Domain.todo("a")),
            ItemAdded(uberto(), book(), Domain.todo("b"))
        };
        let state = List.foldLeft(Events.apply, Events.empty(), events);
        Assert.assertEq(
            expected = Some(ToDoList.ToDoList(book(), List#{Domain.todo("a"), Domain.todo("b")})),
            Events.listFor(state, uberto(), book())
        )
    }

}
