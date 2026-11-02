mod Routes {

    use Domain.{User, ListName, ToDoItem, ToDoList}
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
            Http.route((user, listName) -> Domain.findToDoList(lists, user, listName), request));
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

    /// 同じシナリオを、すべての経路で実行する。
    pub def onEveryRoute(lists: Map[User, List[ToDoList]], scenario: ZettaiActions -> Unit \ {Assert, IO}): Unit \ {Assert, IO} = {
        let routes = domainActions(lists) :: httpActions(lists) :: Nil;
        List.forEach(actions -> {
            scenario(actions);
            actions#close()
        }, routes)
    }

}
