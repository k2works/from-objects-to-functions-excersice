mod TestLoggerPlacement {

    import java.io.IOException
    import java.lang.System

    use Domain.ListName
    use Domain.Commands.ToDoListCommand.CreateToDoList

    def now(): Int64 \ IO = System.currentTimeMillis()

    /// Logger の処理を、サーバーを起動する側（コールバックの外）に置く。
    /// コンパイルは通るが、コールバックの中で Logger を呼ぶと、応答が返らずに接続が切れる。
    /// 理由: Java から呼ばれるメソッドの入口で、中断された効果が UnhandledEffectError（java.lang.Error）に変わり、
    /// 本体は途中で放棄される。コンパイラの検査（E4243）は、効果が型変数の Web.JdkServer.start では通り抜ける。
    @Test
    def aHandlerOutsideTheCallbackLeavesTheRequestUnanswered(): Unit \ {Assert, IO} = {
        run {
            region rc {
                let store = Application.EventStore.inMemory(rc);
                let commands = Application.CommandHub.commandHub(store);
                let hub = Application.Hub.fromStore(store);
                let _ = commands#execute(CreateToDoList(Domain.User.User("uberto"), ListName.ListName("book")));
                let app = Adapter.Logging.observeApp(now, request -> Web.Http.application(hub, commands#execute, request));
                let server = Web.JdkServer.start(0, app);
                let port = Web.JdkServer.port(server);
                let answered = try {
                    let (status, _) = HttpTestClient.get(port, "/todo/uberto/book");
                    Ok(status)
                } catch {
                    case ex: IOException => Err("no response: ${ex.getMessage()}")
                };
                Web.JdkServer.stop(server);
                // 判定は領域の内側で行う（領域の外へ値を出すと、型が合わない）
                match answered {
                    case Err(_)     => ()
                    case Ok(status) => Assert.assertEq(expected = 0, status)
                }
            }
        } with Logger.runWithIO
    }

}
