mod TestCommandHub {

    use Domain.{User, ListName, ToDoList, ToDoStatus}
    use Domain.Errors.ZettaiError
    use Domain.Events.ToDoListEvent.{ListCreated, ItemAdded, ItemStatusChanged}
    use Domain.Commands.ToDoListCommand.{CreateToDoList, AddToDoItem, ChangeItemStatus}

    def uberto(): User = User.User("uberto")

    def book(): ListName = ListName.ListName("book")

    /// 領域の中でだけ変わるストアを使うので、関数は純粋なまま。
    def scenario(commands: List[Domain.Commands.ToDoListCommand]): (List[Result[ZettaiError, Unit]], Option[ToDoList], List[Domain.Events.ToDoListEvent]) = {
        region rc {
            let store = Application.EventStore.inMemory(rc);
            let hub = Application.CommandHub.commandHub(store);
            let results = List.map(hub#execute, commands);
            (results, Domain.Events.listFor(Result.getWithDefault(Domain.Events.empty(), hub#current()), uberto(), book()), Result.getWithDefault(Nil, Result.map(read -> read#events, store#load())))
        }
    }

    @Test
    def commandsBuildUpTheStateThroughEvents(): Unit \ Assert = {
        let (results, list, history) = scenario(List#{
            CreateToDoList(uberto(), book()),
            AddToDoItem(uberto(), book(), Domain.todo("write chapter")),
            ChangeItemStatus(uberto(), book(), "write chapter", ToDoStatus.Done)
        });
        Assert.assertEq(expected = List#{Ok(()), Ok(()), Ok(())}, results);
        Assert.assertEq(
            expected = Some(ToDoList.ToDoList(book(), List#{Domain.ToDoItem.ToDoItem("write chapter", ToDoStatus.Done)})),
            list
        );
        Assert.assertEq(
            expected = List#{
                ListCreated(uberto(), book()),
                ItemAdded(uberto(), book(), Domain.todo("write chapter")),
                ItemStatusChanged(uberto(), book(), "write chapter", ToDoStatus.Done)
            },
            history
        )
    }

    @Test
    def aRejectedCommandLeavesNoEventBehind(): Unit \ Assert = {
        let (results, _, history) = scenario(List#{
            CreateToDoList(uberto(), book()),
            CreateToDoList(uberto(), book())
        });
        Assert.assertEq(expected = List#{Ok(()), Err(ZettaiError.ListAlreadyExists(uberto(), book()))}, results);
        Assert.assertEq(expected = List#{ListCreated(uberto(), book())}, history)
    }

    @Test
    def aCommandOnAMissingListIsRejected(): Unit \ Assert = {
        let (results, list, history) = scenario(List#{AddToDoItem(uberto(), book(), Domain.todo("read"))});
        Assert.assertEq(expected = List#{Err(ZettaiError.ListNotFound(uberto(), book()))}, results);
        Assert.assertEq(expected = None, list);
        Assert.assertEq(expected = Nil, history)
    }

}
