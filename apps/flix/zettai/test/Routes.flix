mod Routes {

    use Domain.{User, ListName, ToDoItem, ToDoList}
    use Errors.ZettaiError
    use Events.ToDoListEvent
    use ZettaiActions.ZettaiActions

    /// 純粋な値を、IO を持つ枠に入れる。経路を同じ型に揃えるための、テスト側だけの宣言。
    def inIO(value: a): a \ IO = unchecked_cast(value as _ \ IO)

    /// ドメインだけを通る経路。HTTP を知らない。
    pub def domainActions(lists: Map[User, List[ToDoList]]): ZettaiActions = {
        name = "domain",
        getToDoList = (user, listName) ->
            inIO(Domain.findToDoList(lists, user, listName)
                |> Option.map(l -> match l { case ToDoList.ToDoList(_, items) => items })),
        close = () -> inIO(())
    }

    /// 実際の HTTP サーバーを起動し、HTML を読み戻す経路。
    pub def httpActions(lists: Map[User, List[ToDoList]]): ZettaiActions \ IO = {
        let server = JdkServer.start(0, request ->
            Http.route(Hub.inMemory(lists), request));
        let port = JdkServer.port(server);
        {
            name = "http",
            getToDoList = (user, listName) -> {
                let User.User(u) = user;
                let ListName.ListName(l) = listName;
                let (status, body) = HttpTestClient.get(port, "/todo/${u}/${l}");
                if (status == 200) Some(HtmlReader.items(body)) else None
            },
            close = () -> JdkServer.stop(server)
        }
    }

    /// リストの集まりを、それを作ったイベントの列に戻す。
    def eventsOf(lists: Map[User, List[ToDoList]]): List[ToDoListEvent] =
        List.flatMap(entry -> match entry {
            case (user, userLists) =>
                List.flatMap(list -> match list {
                    case ToDoList.ToDoList(name, items) =>
                        ToDoListEvent.ListCreated(user, name) ::
                            List.map(item -> ToDoListEvent.ItemAdded(user, name, item), items)
                }, userLists)
        }, Map.toList(lists))

    /// 実際の PostgreSQL にイベントを書き、読み戻して状態を作る経路。
    pub def databaseActions(lists: Map[User, List[ToDoList]]): Result[ZettaiError, ZettaiActions] \ IO = {
        let config = PostgresStore.configFromEnv();
        let table = PostgresStore.freshTableName();
        forM (
            store <- PostgresStore.open(config, table);
            _     <- store#append(eventsOf(lists))
        ) yield {
            name = "database",
            getToDoList = (user, listName) -> match store#load() {
                case Err(_)      => None
                case Ok(history) =>
                    Option.map(l -> match l { case ToDoList.ToDoList(_, items) => items },
                        Events.listFor(Replay.replayFrom(Events.empty(), history), user, listName))
            },
            close = () -> { let _ = PostgresStore.dropTable(config, table); () }
        }
    }

    /// 同じシナリオを、すべての経路で実行する。
    pub def onEveryRoute(lists: Map[User, List[ToDoList]], scenario: ZettaiActions -> Unit \ {Assert, IO}): Unit \ {Assert, IO} = {
        let database = databaseActions(lists);
        let routes = match database {
            case Ok(actions) => domainActions(lists) :: httpActions(lists) :: actions :: Nil
            case Err(e)      => {
                Assert.assertEq(expected = "the database route opens", ToString.toString(e));
                Nil
            }
        };
        List.forEach(actions -> {
            scenario(actions);
            actions#close()
        }, routes)
    }

}
