mod TestRenameOverHttp {

    use Domain.{User, ListName}
    use Domain.Commands.ToDoListCommand.{CreateToDoList, AddToDoItem}

    def uberto(): User = User.User("uberto")

    @Test
    def renamingThroughARealServerMovesTheListAndReportsEveryProblem(): Unit \ {Assert, IO} = {
        region rc {
            let store = Application.EventStore.inMemory(rc);
            let commands = Application.CommandHub.commandHub(store);
            let hub = Application.Hub.fromStore(store);
            let _ = commands#execute(CreateToDoList(uberto(), ListName.ListName("book")));
            let _ = commands#execute(AddToDoItem(uberto(), ListName.ListName("book"), Domain.todo("write chapter")));
            let server = Web.JdkServer.start(0, request -> Web.Http.application(hub, commands#execute, request));
            let port = Web.JdkServer.port(server);
            // 正しい要求なら、名前が変わる
            let (renamed, _) = HttpTestClient.post(port, "/todo/uberto/book/rename", "shop");
            let (oldStatus, _) = HttpTestClient.get(port, "/todo/uberto/book");
            let (newStatus, newBody) = HttpTestClient.get(port, "/todo/uberto/shop");
            // 誤りのある要求なら、誤りがすべて返り、何も変わらない
            let (badStatus, badBody) = HttpTestClient.post(port, "/todo/uberto/shop/rename", String.repeat(41, "/"));
            let (stillThere, _) = HttpTestClient.get(port, "/todo/uberto/shop");
            Web.JdkServer.stop(server);
            Assert.assertEq(expected = 200, renamed);
            Assert.assertEq(expected = 404, oldStatus);
            Assert.assertEq(expected = 200, newStatus);
            Assert.assertTrue(String.contains(substr = "<li>write chapter</li>", newBody));
            Assert.assertEq(expected = 400, badStatus);
            Assert.assertTrue(String.contains(substr = "40 文字以内", badBody));
            Assert.assertTrue(String.contains(substr = "使えない文字", badBody));
            Assert.assertEq(expected = 200, stillThere)
        }
    }

}
