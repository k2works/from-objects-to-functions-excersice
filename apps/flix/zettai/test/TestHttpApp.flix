mod TestHttpApp {

    use Domain.{User, ListName, ToDoItem, ToDoList}
    use Http.{Request, Response}

    def book(): ToDoList =
        ToDoList.ToDoList(
            ListName.ListName("book"),
            List#{Domain.todo("write chapter"), Domain.todo("publish book")}
        )

    def hub(): Hub.ToDoListHub[{}] = Hub.inMemory(Map#{User.User("uberto") => List#{book()}})

    def get(path: String): Request = { method = "GET", path = path }

    @Test
    def returnsTheListAsHtml(): Unit \ Assert = {
        let response = Http.route(hub(), get("/todo/uberto/book"));
        Assert.assertEq(expected = 200, response#status);
        Assert.assertEq(expected = "text/html; charset=utf-8", response#contentType);
        Assert.assertTrue(String.contains(substr = "<li>write chapter</li>", response#body))
    }

    @Test
    def returnsNotFoundForAMissingList(): Unit \ Assert =
        Assert.assertEq(expected = 404, Http.route(hub(), get("/todo/uberto/missing"))#status)

    @Test
    def returnsNotFoundForAnUnknownPath(): Unit \ Assert =
        Assert.assertEq(expected = 404, Http.route(hub(), get("/other"))#status)

    @Test
    def returnsMethodNotAllowedForNonGet(): Unit \ Assert =
        Assert.assertEq(expected = 405, Http.route(hub(), { method = "POST", path = "/todo/uberto/book" })#status)

}
