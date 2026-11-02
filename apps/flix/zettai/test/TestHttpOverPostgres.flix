mod TestHttpOverPostgres {

    use Domain.{User, ListName}
    use Errors.ZettaiError
    use Commands.ToDoListCommand.{CreateToDoList, AddToDoItem}
    use Events.ToDoListEvent

    def uberto(): User = User.User("uberto")

    def book(): ListName = ListName.ListName("book")

    /// HTTP から PostgreSQL までを通す。読み取りもコマンドも、同じ表のイベントを見る。
    @Test
    def requestsThroughARealServerReachPostgres(): Unit \ {Assert, IO} = {
        let config = PostgresStore.configFromEnv();
        let table = PostgresStore.freshTableName();
        match (PostgresStore.open(config, table), PostgresStore.open(config, table)) {
            case (Ok(store), Ok(other)) => {
                let commands = CommandHub.commandHub(store);
                let hub = Hub.fromStore(store);
                let _ = commands#execute(CreateToDoList(uberto(), book()));
                let _ = commands#execute(AddToDoItem(uberto(), book(), Domain.todo("write chapter & <publish>")));
                let server = JdkServer.start(0, request -> Http.application(hub, commands#execute, request));
                let port = JdkServer.port(server);
                // 読む: データベースのイベントから作った HTML が、特殊文字をエスケープして返る
                let (readStatus, readBody) = HttpTestClient.get(port, "/todo/uberto/book");
                // 書く: 名前を変えると、PostgreSQL に 3 件のイベントが足される
                let (renamed, _) = HttpTestClient.post(port, "/todo/uberto/book/rename", "shop");
                let (oldStatus, _) = HttpTestClient.get(port, "/todo/uberto/book");
                let (newStatus, newBody) = HttpTestClient.get(port, "/todo/uberto/shop");
                // 誤りのある要求は、400 を返し、PostgreSQL に何も書かない
                let (badStatus, badBody) = HttpTestClient.post(port, "/todo/uberto/shop/rename", String.repeat(41, "/"));
                // 存在しないリストは 404
                let (missingStatus, _) = HttpTestClient.post(port, "/todo/uberto/nothing/rename", "x");
                JdkServer.stop(server);
                Assert.assertEq(expected = 200, readStatus);
                Assert.assertTrue(String.contains(substr = "<li>write chapter &amp; &lt;publish&gt;</li>", readBody));
                Assert.assertEq(expected = 200, renamed);
                Assert.assertEq(expected = 404, oldStatus);
                Assert.assertEq(expected = 200, newStatus);
                Assert.assertTrue(String.contains(substr = "write chapter &amp; &lt;publish&gt;", newBody));
                Assert.assertEq(expected = 400, badStatus);
                Assert.assertTrue(String.contains(substr = "使えない文字", badBody));
                Assert.assertEq(expected = 404, missingStatus);
                // 別の接続（別のストア）から見ても、書いたものが残っている
                match other#load() {
                    case Ok(read) => {
                        Assert.assertEq(expected = 5, read#version);
                        Assert.assertEq(
                            expected = List#{
                                ToDoListEvent.ListCreated(uberto(), book()),
                                ToDoListEvent.ItemAdded(uberto(), book(), Domain.todo("write chapter & <publish>")),
                                ToDoListEvent.ListRenamed(uberto(), book(), ListName.ListName("shop")),
                                ToDoListEvent.ListCreated(uberto(), ListName.ListName("shop")),
                                ToDoListEvent.ItemAdded(uberto(), ListName.ListName("shop"), Domain.todo("write chapter & <publish>"))
                            },
                            read#events
                        )
                    }
                    case Err(e) => Assert.assertEq(expected = "loaded", ToString.toString(e))
                };
                // 古い版を持つ別の書き手は、HTTP が進めた版に対して断られる
                Assert.assertEq(
                    expected = Err(ZettaiError.VersionConflict(0, 5)),
                    other#append(0, ToDoListEvent.ListCreated(uberto(), ListName.ListName("late")) :: Nil)
                )
            }
            case _ => Assert.assertEq(expected = "opened", "not opened")
        };
        let _ = PostgresStore.dropTable(config, table);
        ()
    }

}
