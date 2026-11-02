mod TestHttpApp {

    use Domain.{User, ListName, ToDoItem, ToDoList}
    use Http.{Request, Response}

    def book(): ToDoList =
        ToDoList.ToDoList(
            ListName.ListName("book"),
            List#{ToDoItem.ToDoItem("write chapter"), ToDoItem.ToDoItem("publish book")}
        )

    def fetcher(user: User, name: ListName): Option[ToDoList] =
        Domain.findToDoList(Map#{User.User("uberto") => List#{book()}}, user, name)

    def get(path: String): Request = { method = "GET", path = path }

    @Test
    def returnsTheListAsHtml(): Unit \ Assert = {
        let response = Http.route(fetcher, get("/todo/uberto/book"));
        Assert.assertEq(expected = 200, response#status);
        Assert.assertEq(expected = "text/html; charset=utf-8", response#contentType);
        Assert.assertTrue(String.contains(substr = "<li>write chapter</li>", response#body))
    }

    @Test
    def returnsNotFoundForAMissingList(): Unit \ Assert =
        Assert.assertEq(expected = 404, Http.route(fetcher, get("/todo/uberto/missing"))#status)

    @Test
    def returnsNotFoundForAnUnknownPath(): Unit \ Assert =
        Assert.assertEq(expected = 404, Http.route(fetcher, get("/other"))#status)

    @Test
    def returnsMethodNotAllowedForNonGet(): Unit \ Assert =
        Assert.assertEq(expected = 405, Http.route(fetcher, { method = "POST", path = "/todo/uberto/book" })#status)

}
