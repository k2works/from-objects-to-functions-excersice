mod TestProjection {

    use Domain.{User, ListName, ToDoStatus}
    use Events.ToDoListEvent
    use Events.ToDoListEvent.{ListCreated, ItemAdded, ItemStatusChanged}
    use Projection.ToDoListRow

    def uberto(): User = User.User("uberto")

    def alice(): User = User.User("alice")

    def book(): ListName = ListName.ListName("book")

    def shop(): ListName = ListName.ListName("shop")

    @Test
    def projectsOneRowPerListWithItemAndDoneCounts(): Unit \ Assert = {
        let events = List#{
            ListCreated(uberto(), book()),
            ItemAdded(uberto(), book(), Domain.todo("a")),
            ItemAdded(uberto(), book(), Domain.todo("b")),
            ItemStatusChanged(uberto(), book(), "a", ToDoStatus.Done)
        };
        Assert.assertEq(
            expected = List#{ToDoListRow.ToDoListRow(book(), 2, 1)},
            Projection.projectFrom(uberto(), events)
        )
    }

    @Test
    def rowsAreOrderedByListName(): Unit \ Assert = {
        let events = List#{ListCreated(uberto(), shop()), ListCreated(uberto(), book())};
        Assert.assertEq(
            expected = List#{ToDoListRow.ToDoListRow(book(), 0, 0), ToDoListRow.ToDoListRow(shop(), 0, 0)},
            Projection.projectFrom(uberto(), events)
        )
    }

    @Test
    def onlyTheListsOfTheGivenUserAppear(): Unit \ Assert = {
        let events = List#{ListCreated(uberto(), book()), ListCreated(alice(), shop())};
        Assert.assertEq(expected = List#{ToDoListRow.ToDoListRow(book(), 0, 0)}, Projection.projectFrom(uberto(), events));
        Assert.assertEq(expected = List#{ToDoListRow.ToDoListRow(shop(), 0, 0)}, Projection.projectFrom(alice(), events))
    }

    @Test
    def anItemAddedToAMissingListMakesNoRow(): Unit \ Assert =
        Assert.assertEq(expected = Nil, Projection.projectFrom(uberto(), List#{ItemAdded(uberto(), book(), Domain.todo("a"))}))

    @Test
    def aStatusChangeCountsOnlyTheFirstItemWithThatDescription(): Unit \ Assert = {
        let events = List#{
            ListCreated(uberto(), book()),
            ItemAdded(uberto(), book(), Domain.todo("a")),
            ItemAdded(uberto(), book(), Domain.todo("a")),
            ItemStatusChanged(uberto(), book(), "a", ToDoStatus.Done)
        };
        Assert.assertEq(expected = List#{ToDoListRow.ToDoListRow(book(), 2, 1)}, Projection.projectFrom(uberto(), events))
    }

    /// 状態を再生してから数え直した行。射影と同じ答えになるはず。
    def rowsFromState(user: User, events: List[ToDoListEvent]): List[ToDoListRow] = {
        let state = Replay.replayFrom(Events.empty(), events);
        match Events.listFor(state, user, book()) {
            case None => Nil
            case Some(Domain.ToDoList.ToDoList(name, items)) =>
                List#{ToDoListRow.ToDoListRow(
                    name,
                    List.length(items),
                    List.count(item -> match item { case Domain.ToDoItem.ToDoItem(_, s) => s == ToDoStatus.Done }, items)
                )}
        }
    }

    @Test
    def theProjectionMatchesTheCountsOfTheReplayedState(): Unit \ Assert =
        List.forEach(events ->
            List.forEach(user ->
                Assert.assertEq(expected = rowsFromState(user, events), Projection.projectFrom(user, events)),
                List#{uberto(), alice()}),
            Gen.projectionListsUpTo(4))

}
