mod TestTransition {

    use Domain.{User, ListName}
    use Domain.Events.ToDoListEvent
    use Domain.Events.ToDoListState
    use Domain.Events.Transition.StateTransition

    def seeds(): List[ToDoListState] =
        List.map(events -> Domain.Events.Replay.replayFrom(Domain.Events.empty(), events), Gen.eventListsUpTo(1))

    def transitions(): List[StateTransition] =
        List.map(Domain.Events.Transition.fromEvents, Gen.eventListsUpTo(1))

    /// 2 つの変換が、用意した初期状態のすべてで同じ結果を返すこと。
    def sameOnEverySeed(a: StateTransition, b: StateTransition): Bool =
        List.forAll(seed -> Domain.Events.Transition.applyTo(a, seed) == Domain.Events.Transition.applyTo(b, seed), seeds())

    @Test
    def associativity(): Unit \ Assert =
        List.forEach(a ->
            List.forEach(b ->
                List.forEach(c ->
                    Assert.assertTrue(sameOnEverySeed(
                        Domain.Events.Transition.andThen(Domain.Events.Transition.andThen(a, b), c),
                        Domain.Events.Transition.andThen(a, Domain.Events.Transition.andThen(b, c)))),
                    transitions()),
                transitions()),
            transitions())

    @Test
    def identityOnTheLeftAndOnTheRight(): Unit \ Assert =
        List.forEach(t -> {
            Assert.assertTrue(sameOnEverySeed(Domain.Events.Transition.andThen(Domain.Events.Transition.identity(), t), t));
            Assert.assertTrue(sameOnEverySeed(Domain.Events.Transition.andThen(t, Domain.Events.Transition.identity()), t))
        }, transitions())

    @Test
    def foldingTransitionsEqualsReplayingEvents(): Unit \ Assert =
        List.forEach(events ->
            List.forEach(seed ->
                Assert.assertEq(
                    expected = Domain.Events.Replay.replayFrom(seed, events),
                    Domain.Events.Transition.applyTo(Domain.Events.Transition.fromEvents(events), seed)),
                seeds()),
            Gen.eventListsUpTo(3))

    @Test
    def theOrderOfCompositionMatters(): Unit \ Assert = {
        let created = Domain.Events.Transition.fromEvent(ToDoListEvent.ListCreated(User.User("uberto"), ListName.ListName("book")));
        let added = Domain.Events.Transition.fromEvent(ToDoListEvent.ItemAdded(User.User("uberto"), ListName.ListName("book"), Domain.todo("a")));
        Assert.assertFalse(sameOnEverySeed(Domain.Events.Transition.andThen(created, added), Domain.Events.Transition.andThen(added, created)))
    }

}
