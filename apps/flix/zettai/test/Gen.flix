mod Gen {

    use Domain.{User, ListName}
    use Events.ToDoListEvent
    use Events.ToDoListEvent.{ListCreated, ItemAdded}

    /// 乱数ではなく、小さな範囲の全パターンを並べる。
    /// 2 人の利用者・2 つのリスト名・2 つの項目から作った、起こりうるイベントの全体。
    pub def universe(): List[ToDoListEvent] = {
        let users = List#{User.User("uberto"), User.User("alice")};
        let names = List#{ListName.ListName("book"), ListName.ListName("food")};
        let items = List#{Domain.todo("a"), Domain.todo("b")};
        let created = List.flatMap(u -> List.map(n -> ListCreated(u, n), names), users);
        let added = List.flatMap(u -> List.flatMap(n -> List.map(i -> ItemAdded(u, n, i), items), names), users);
        created ::: added
    }

    /// ちょうど k 個のイベントからなる列の全パターン。
    def ofLength(k: Int32, xs: List[a]): List[List[a]] =
        if (k <= 0)
            Nil :: Nil
        else
            List.flatMap(x -> List.map(rest -> x :: rest, ofLength(k - 1, xs)), xs)

    /// 0 個から n 個までのイベント列の全パターン。
    pub def eventListsUpTo(n: Int32): List[List[ToDoListEvent]] =
        List.flatMap(k -> ofLength(k, universe()), List.range(0, n + 1))

    /// 射影の確認用。状態の変更まで含めた、起こりうるイベントの全体（2 人・1 リスト・1 項目・2 状態）。
    pub def projectionUniverse(): List[ToDoListEvent] = {
        let users = List#{User.User("uberto"), User.User("alice")};
        let name = ListName.ListName("book");
        let created = List.map(u -> ListCreated(u, name), users);
        let added = List.map(u -> ItemAdded(u, name, Domain.todo("a")), users);
        let changed = List.flatMap(u -> List.map(s -> Events.ToDoListEvent.ItemStatusChanged(u, name, "a", s),
            List#{Domain.ToDoStatus.InProgress, Domain.ToDoStatus.Done}), users);
        created ::: added ::: changed
    }

    /// 0 個から n 個までの、射影用のイベント列の全パターン。
    pub def projectionListsUpTo(n: Int32): List[List[ToDoListEvent]] =
        List.flatMap(k -> ofLength(k, projectionUniverse()), List.range(0, n + 1))

}
