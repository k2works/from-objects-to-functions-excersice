mod TestTransition {

    use Domain.{User, ListName}
    use Events.ToDoListEvent
    use Events.ToDoListState
    use Transition.StateTransition

    def seeds(): List[ToDoListState] =
        List.map(events -> Replay.replayFrom(Events.empty(), events), Gen.eventListsUpTo(1))

    def transitions(): List[StateTransition] =
        List.map(Transition.fromEvents, Gen.eventListsUpTo(1))

    /// 2 つの変換が、用意した初期状態のすべてで同じ結果を返すこと。
    def sameOnEverySeed(a: StateTransition, b: StateTransition): Bool =
        List.forAll(seed -> Transition.applyTo(a, seed) == Transition.applyTo(b, seed), seeds())

    @Test
    def associativity(): Unit \ Assert =
        List.forEach(a ->
            List.forEach(b ->
                List.forEach(c ->
                    Assert.assertTrue(sameOnEverySeed(
                        Transition.andThen(Transition.andThen(a, b), c),
                        Transition.andThen(a, Transition.andThen(b, c)))),
                    transitions()),
                transitions()),
            transitions())

    @Test
    def identityOnTheLeftAndOnTheRight(): Unit \ Assert =
        List.forEach(t -> {
            Assert.assertTrue(sameOnEverySeed(Transition.andThen(Transition.identity(), t), t));
            Assert.assertTrue(sameOnEverySeed(Transition.andThen(t, Transition.identity()), t))
        }, transitions())

    @Test
    def foldingTransitionsEqualsReplayingEvents(): Unit \ Assert =
        List.forEach(events ->
            List.forEach(seed ->
                Assert.assertEq(
                    expected = Replay.replayFrom(seed, events),
                    Transition.applyTo(Transition.fromEvents(events), seed)),
                seeds()),
            Gen.eventListsUpTo(3))

    @Test
    def theOrderOfCompositionMatters(): Unit \ Assert = {
        let created = Transition.fromEvent(ToDoListEvent.ListCreated(User.User("uberto"), ListName.ListName("book")));
        let added = Transition.fromEvent(ToDoListEvent.ItemAdded(User.User("uberto"), ListName.ListName("book"), Domain.todo("a")));
        Assert.assertFalse(sameOnEverySeed(Transition.andThen(created, added), Transition.andThen(added, created)))
    }

}
