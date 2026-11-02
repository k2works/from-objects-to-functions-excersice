mod TestLoggerPlacement {

    import java.io.IOException
    import java.lang.System

    use Domain.ListName
    use Commands.ToDoListCommand.CreateToDoList

    def now(): Int64 \ IO = System.currentTimeMillis()

    /// Logger の処理を、サーバーを起動する側（コールバックの外）に置く。
    /// コンパイルは通るが、コールバックの中で Logger を呼ぶと、応答が返らずに接続が切れる。
    @Test
    def aHandlerOutsideTheCallbackLeavesTheRequestUnanswered(): Unit \ {Assert, IO} = {
        run {
            region rc {
                let store = EventStore.inMemory(rc);
                let commands = CommandHub.commandHub(store);
                let hub = Hub.fromStore(store);
                let _ = commands#execute(CreateToDoList(Domain.User.User("uberto"), ListName.ListName("book")));
                let app = Logging.observeApp(now, request -> Http.application(hub, commands#execute, request));
                let server = JdkServer.start(0, app);
                let port = JdkServer.port(server);
                let answered = try {
                    let (status, _) = HttpTestClient.get(port, "/todo/uberto/book");
                    Ok(status)
                } catch {
                    case ex: IOException => Err("no response: ${ex.getMessage()}")
                };
                JdkServer.stop(server);
                // 判定は領域の内側で行う（領域の外へ値を出すと、型が合わない）
                match answered {
                    case Err(_)     => ()
                    case Ok(status) => Assert.assertEq(expected = 0, status)
                }
            }
        } with Logger.runWithIO
    }

}
