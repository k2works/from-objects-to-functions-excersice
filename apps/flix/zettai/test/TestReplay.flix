mod TestReplay {

    use Events.ToDoListEvent
    use Events.ToDoListState

    def seeds(): List[ToDoListState] =
        List.map(events -> Replay.replayFrom(Events.empty(), events), Gen.eventListsUpTo(1))

    @Test
    def anEmptyListOfEventsKeepsTheInitialState(): Unit \ Assert =
        List.forEach(seed -> Assert.assertEq(expected = seed, Replay.replayFrom(seed, Nil)), seeds())

    @Test
    def recursionAndFoldGiveTheSameState(): Unit \ Assert =
        List.forEach(seed ->
            List.forEach(events ->
                Assert.assertEq(expected = Replay.replayByRecursion(seed, events), Replay.replayFrom(seed, events)),
                Gen.eventListsUpTo(3)),
            seeds())

    @Test
    def replayingInTwoPartsGivesTheSameStateAsReplayingAtOnce(): Unit \ Assert =
        List.forEach(a ->
            List.forEach(b ->
                Assert.assertEq(
                    expected = Replay.replayFrom(Events.empty(), a ::: b),
                    Replay.replayFrom(Replay.replayFrom(Events.empty(), a), b)),
                Gen.eventListsUpTo(2)),
            Gen.eventListsUpTo(2))

}
