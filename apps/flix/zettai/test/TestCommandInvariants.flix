mod TestCommandInvariants {

    use Domain.{User, ListName, ToDoStatus}
    use Commands.ToDoListCommand
    use Commands.ToDoListCommand.{CreateToDoList, AddToDoItem, ChangeItemStatus}
    use Events.ToDoListEvent
    use Events.ToDoListEvent.ItemStatusChanged

    def uberto(): User = User.User("uberto")

    def book(): ListName = ListName.ListName("book")

    /// 1 人・1 リスト・1 項目に絞った、起こりうるコマンドの全体。
    def commands(): List[ToDoListCommand] = List#{
        CreateToDoList(uberto(), book()),
        AddToDoItem(uberto(), book(), Domain.todo("a")),
        ChangeItemStatus(uberto(), book(), "a", ToDoStatus.InProgress),
        ChangeItemStatus(uberto(), book(), "a", ToDoStatus.Done),
        ChangeItemStatus(uberto(), book(), "a", ToDoStatus.Blocked)
    }

    def ofLength(k: Int32): List[List[ToDoListCommand]] =
        if (k <= 0)
            Nil :: Nil
        else
            List.flatMap(c -> List.map(rest -> c :: rest, ofLength(k - 1)), commands())

    /// 長さ 0 から 4 までのコマンド列の全パターン（781 通り）。
    def allSequences(): List[List[ToDoListCommand]] =
        List.flatMap(ofLength, List.range(0, 5))

    def accepted(result: Result[e, a]): Bool =
        match result {
            case Ok(_)  => true
            case Err(_) => false
        }

    /// コマンド列を順に実行し、(受理されたか, 最終のイベント履歴) を返す。
    def execute(sequence: List[ToDoListCommand]): (List[Bool], List[ToDoListEvent]) = {
        region rc {
            let store = EventStore.inMemory(rc);
            let hub = CommandHub.commandHub(store);
            let results = List.map(command -> accepted(hub#execute(command)), sequence);
            (results, store#load())
        }
    }

    def statusesChangedTo(history: List[ToDoListEvent]): List[ToDoStatus] =
        List.filterMap(event -> match event {
            case ItemStatusChanged(_, _, _, status) => Some(status)
            case _                                  => None
        }, history)

    /// Done が現れた後に、状態の変更が続かないこと。
    def nothingFollowsDone(statuses: List[ToDoStatus]): Bool =
        match List.findLeft(s -> s == ToDoStatus.Done, statuses) {
            case None    => true
            case Some(_) => match List.dropWhile(s -> s != ToDoStatus.Done, statuses) {
                case _ :: rest => rest == Nil
                case Nil       => true
            }
        }

    @Test
    def eachAcceptedCommandLeavesExactlyOneEvent(): Unit \ Assert =
        List.forEach(sequence -> {
            let (results, history) = execute(sequence);
            Assert.assertEq(expected = List.count(identity, results), List.length(history))
        }, allSequences())

    @Test
    def nothingChangesAfterAnItemIsDone(): Unit \ Assert =
        List.forEach(sequence -> {
            let (_, history) = execute(sequence);
            Assert.assertTrue(nothingFollowsDone(statusesChangedTo(history)))
        }, allSequences())

    @Test
    def theSequencesActuallyReachTheDoneState(): Unit \ Assert = {
        let reached = List.count(sequence -> {
            let (_, history) = execute(sequence);
            List.exists(s -> s == ToDoStatus.Done, statusesChangedTo(history))
        }, allSequences());
        Assert.assertTrue(reached > 0)
    }

}
