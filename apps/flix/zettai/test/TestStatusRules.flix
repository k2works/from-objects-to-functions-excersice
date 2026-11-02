mod TestStatusRules {

    use Domain.ToDoStatus
    use Domain.ToDoStatus.{Todo, InProgress, Done, Blocked}

    /// 遷移表そのもの。(現在, 次, 許可されるか) を 4 x 4 = 16 組すべて書く。
    def table(): List[(ToDoStatus, ToDoStatus, Bool)] = List#{
        (Todo, Todo, false),       (Todo, InProgress, true),       (Todo, Done, true),       (Todo, Blocked, false),
        (InProgress, Todo, false), (InProgress, InProgress, false), (InProgress, Done, true), (InProgress, Blocked, true),
        (Done, Todo, false),       (Done, InProgress, false),      (Done, Done, false),      (Done, Blocked, false),
        (Blocked, Todo, false),    (Blocked, InProgress, true),    (Blocked, Done, false),   (Blocked, Blocked, false)
    }

    @Test
    def theTableCoversEveryPairOfStatuses(): Unit \ Assert = {
        let all = Domain.allStatuses();
        let pairs = List.flatMap(a -> List.map(b -> (a, b), all), all);
        let covered = List.map(row -> match row { case (a, b, _) => (a, b) }, table());
        Assert.assertEq(expected = 16, List.length(pairs));
        Assert.assertEq(expected = pairs, covered)
    }

    @Test
    def eachPairIsAllowedOrRejectedAsTheTableSays(): Unit \ Assert =
        List.forEach(row -> match row {
            case (current, next, allowed) =>
                Assert.assertEq(expected = (current, next, allowed), (current, next, Domain.canTransition(current, next)))
        }, table())

    @Test
    def exactlyFiveTransitionsAreAllowed(): Unit \ Assert =
        Assert.assertEq(expected = 5, List.length(List.filter(row -> match row { case (_, _, allowed) => allowed }, table())))

}
