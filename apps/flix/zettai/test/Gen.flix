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

}
