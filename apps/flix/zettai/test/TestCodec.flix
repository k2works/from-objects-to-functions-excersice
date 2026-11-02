mod TestCodec {

    use Domain.{User, ListName, ToDoStatus}
    use Errors.ZettaiError
    use Events.ToDoListEvent
    use Events.ToDoListEvent.{ListCreated, ItemAdded, ItemStatusChanged}

    def uberto(): User = User.User("uberto")

    def book(): ListName = ListName.ListName("book")

    /// 4 つの状態すべてを含む、イベントの全体。
    def everyKindOfEvent(): List[ToDoListEvent] = {
        let statuses = Domain.allStatuses();
        let added = List.map(s -> ItemAdded(uberto(), book(), Domain.ToDoItem.ToDoItem("a & <b>", s)), statuses);
        let changed = List.map(s -> ItemStatusChanged(uberto(), book(), "a & <b>", s), statuses);
        ListCreated(uberto(), book()) :: Events.ToDoListEvent.ListRenamed(uberto(), book(), ListName.ListName("shop & <co>")) :: (added ::: changed)
    }

    @Test
    def decodingWhatWasEncodedGivesBackTheEvent(): Unit \ Assert =
        List.forEach(event -> Assert.assertEq(expected = Ok(event), Codec.decode(Codec.encode(event))),
            everyKindOfEvent() ::: Gen.universe() ::: Gen.projectionUniverse())

    @Test
    def aRowOfAnUnknownKindIsRejected(): Unit \ Assert =
        Assert.assertEq(
            expected = Err(ZettaiError.StorageFailure("unknown event kind: Deleted")),
            Codec.decode({ kind = "Deleted", user = "u", list = "l", description = "", status = "" })
        )

    @Test
    def aRowWithAnUnknownStatusIsRejected(): Unit \ Assert =
        Assert.assertEq(
            expected = Err(ZettaiError.StorageFailure("unknown status: Archived")),
            Codec.decode({ kind = "ItemStatusChanged", user = "u", list = "l", description = "a", status = "Archived" })
        )

}
