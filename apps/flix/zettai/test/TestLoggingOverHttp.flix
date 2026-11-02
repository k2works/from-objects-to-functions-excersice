mod TestLoggingOverHttp {

    import java.lang.System

    use Domain.ListName
    use Domain.Commands.ToDoListCommand.CreateToDoList

    def now(): Int64 \ IO = System.currentTimeMillis()

    @Test
    def aServerLogsEachRequestAndStillAnswersTheSame(): Unit \ {Assert, IO} = {
        region rc {
            let store = Application.EventStore.inMemory(rc);
            let commands = Application.CommandHub.commandHub(store);
            let hub = Application.Hub.fromStore(store);
            let _ = commands#execute(CreateToDoList(Domain.User.User("uberto"), ListName.ListName("book")));
            let app = Adapter.Logging.observeApp(now, request -> Web.Http.application(hub, commands#execute, request));
            // サーバーのコールバックは、ハンドラの外で動く。Logger は、コールバックの内側で処理する
            let server = Web.JdkServer.start(0, request -> run { app(request) } with Logger.runWithIO);
            let port = Web.JdkServer.port(server);
            let (found, _) = HttpTestClient.get(port, "/todo/uberto/book");
            let (missing, _) = HttpTestClient.get(port, "/todo/uberto/none");
            Web.JdkServer.stop(server);
            Assert.assertEq(expected = 200, found);
            Assert.assertEq(expected = 404, missing)
        }
    }

}
