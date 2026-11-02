mod TestReplay {

    use Domain.Events.ToDoListEvent
    use Domain.Events.ToDoListState

    def seeds(): List[ToDoListState] =
        List.map(events -> Domain.Events.Replay.replayFrom(Domain.Events.empty(), events), Gen.eventListsUpTo(1))

    @Test
    def anEmptyListOfEventsKeepsTheInitialState(): Unit \ Assert =
        List.forEach(seed -> Assert.assertEq(expected = seed, Domain.Events.Replay.replayFrom(seed, Nil)), seeds())

    @Test
    def recursionAndFoldGiveTheSameState(): Unit \ Assert =
        List.forEach(seed ->
            List.forEach(events ->
                Assert.assertEq(expected = Domain.Events.Replay.replayByRecursion(seed, events), Domain.Events.Replay.replayFrom(seed, events)),
                Gen.eventListsUpTo(3)),
            seeds())

    @Test
    def replayingInTwoPartsGivesTheSameStateAsReplayingAtOnce(): Unit \ Assert =
        List.forEach(a ->
            List.forEach(b ->
                Assert.assertEq(
                    expected = Domain.Events.Replay.replayFrom(Domain.Events.empty(), a ::: b),
                    Domain.Events.Replay.replayFrom(Domain.Events.Replay.replayFrom(Domain.Events.empty(), a), b)),
                Gen.eventListsUpTo(2)),
            Gen.eventListsUpTo(2))

}
