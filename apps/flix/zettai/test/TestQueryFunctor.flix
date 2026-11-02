mod TestQueryFunctor {

    use Domain.{User, ListName, ToDoStatus}
    use Errors.ZettaiError
    use Projection.ToDoListRow

    /// 第 9 章で読み出しが失敗しうるようになったときの、query の結果の形。
    type alias QueryResult = Result[ZettaiError, List[ToDoListRow]]

    def results(): List[QueryResult] = {
        let user = User.User("uberto");
        let rows = List.map(events -> Ok(Projection.projectFrom(user, events)), Gen.projectionListsUpTo(2));
        let errors = List#{
            Err(ZettaiError.ListNotFound(user, ListName.ListName("book"))),
            Err(ZettaiError.InvalidTransition("a", ToDoStatus.Todo, ToDoStatus.Blocked))
        };
        rows ::: errors
    }

    def rowToInt(): List[ToDoListRow -> Int32] = List#{
        row -> match row { case ToDoListRow.ToDoListRow(_, items, done) => items * 2 + done },
        row -> match row { case ToDoListRow.ToDoListRow(_, items, _) => items }
    }

    def intToText(): List[Int32 -> String] = List#{n -> "n${n}", n -> "x${n + 1}"}

    /// Result と List を重ねた map。内側の List.map を Result.map の中で行う。
    def mapRows(f: ToDoListRow -> b, result: Result[ZettaiError, List[ToDoListRow]]): Result[ZettaiError, List[b]] =
        Result.map(rows -> List.map(f, rows), result)

    @Test
    def theNestedMapPreservesIdentity(): Unit \ Assert =
        List.forEach(r -> Assert.assertEq(expected = r, mapRows(row -> row, r)), results())

    @Test
    def theNestedMapComposes(): Unit \ Assert =
        List.forEach(r ->
            List.forEach(f ->
                List.forEach(g ->
                    Assert.assertEq(
                        expected = mapRows(row -> g(f(row)), r),
                        Result.map(values -> List.map(g, values), mapRows(f, r))),
                    intToText()),
                rowToInt()),
            results())

    @Test
    def aFailureFlowsThroughBothLayersUntouched(): Unit \ Assert = {
        let failure: QueryResult = Err(ZettaiError.ListNotFound(User.User("uberto"), ListName.ListName("book")));
        Assert.assertEq(expected = failure, mapRows(row -> row, failure))
    }

}
