mod TestRenameRoute {

    use Domain.{User, ListName}
    use Errors.ZettaiError
    use Commands.ToDoListCommand
    use Http.Request

    def post(path: String, body: String): Request = { method = "POST", path = path, body = body }

    /// 実行した結果を固定で返す、コマンドの入口の代わり。
    def executing(result: Result[ZettaiError, Unit]): ToDoListCommand -> Result[ZettaiError, Unit] =
        _command -> result

    def contains(text: String, part: String): Bool = String.contains(substr = part, text)

    @Test
    def everyProblemIsReportedInOneResponse(): Unit \ Assert = {
        let response = Http.routeRename(executing(Ok(())), post("/todo/ /a/rename", String.repeat(41, "/")));
        Assert.assertEq(expected = 400, response#status);
        // 利用者名・リスト名・新しい名前の誤りが、1 回の応答にすべて入る
        Assert.assertTrue(contains(response#body, "利用者名"));
        Assert.assertTrue(contains(response#body, "新しい名前"));
        Assert.assertEq(expected = 3, List.length(String.splitOn(substr = "\n- ", response#body)) - 1)
    }

    @Test
    def theResponseNeverEchoesTheInput(): Unit \ Assert = {
        let response = Http.routeRename(executing(Ok(())), post("/todo/uberto/book/rename", "secret/name"));
        Assert.assertEq(expected = 400, response#status);
        Assert.assertFalse(contains(response#body, "secret"))
    }

    @Test
    def aValidRequestRunsTheRenameCommand(): Unit \ Assert = {
        let response = Http.routeRename(executing(Ok(())), post("/todo/uberto/book/rename", "shop"));
        Assert.assertEq(expected = 200, response#status)
    }

    @Test
    def aRefusalFromTheDomainKeepsItsOwnStatus(): Unit \ Assert = {
        let missing = Err(ZettaiError.ListNotFound(User.User("uberto"), ListName.ListName("book")));
        let taken = Err(ZettaiError.ListAlreadyExists(User.User("uberto"), ListName.ListName("shop")));
        Assert.assertEq(expected = 404, Http.routeRename(executing(missing), post("/todo/uberto/book/rename", "shop"))#status);
        Assert.assertEq(expected = 400, Http.routeRename(executing(taken), post("/todo/uberto/book/rename", "shop"))#status)
    }

    @Test
    def anotherPathIsNotARenameRequest(): Unit \ Assert =
        Assert.assertEq(expected = 404, Http.routeRename(executing(Ok(())), post("/todo/uberto/book", "shop"))#status)

}
