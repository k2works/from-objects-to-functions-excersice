mod TestEffectDi {

    use Domain.{User, ListName, ToDoList}
    use Web.Http.{Request, Response}

    def lists(): Map[User, List[ToDoList]] =
        Map#{
            User.User("uberto") => List#{
                ToDoList.ToDoList(ListName.ListName("book"), List#{Domain.todo("write chapter")})
            }
        }

    def get(path: String): Request = { method = "GET", path = path, body = "" }

    /// 関数値のレコードで依存を渡す版
    def byHub(path: String): Response =
        Web.Http.route(Application.Hub.inMemory(lists()), get(path))

    /// 効果ハンドラで依存を差し替える版
    def byHandler(path: String): Response =
        Application.Lookup.handleInMemory(lists(), () -> Web.Http.routeWithEffect(get(path)))

    @Test
    def theHandlerSuppliesTheDependency(): Unit \ Assert =
        Assert.assertEq(expected = 200, byHandler("/todo/uberto/book")#status)

    @Test
    def theHandlerAnswersNotFoundForAMissingList(): Unit \ Assert =
        Assert.assertEq(expected = 404, byHandler("/todo/uberto/missing")#status)

    @Test
    def bothStylesGiveTheSameResponse(): Unit \ Assert = {
        let paths = List#{"/todo/uberto/book", "/todo/uberto/missing", "/todo/nobody/book", "/other", "/todo/uberto"};
        List.forEach(path -> {
            let a = byHub(path);
            let b = byHandler(path);
            Assert.assertEq(expected = (a#status, a#contentType, a#body), (b#status, b#contentType, b#body))
        }, paths)
    }

}
