mod TestHub {

    use Domain.{User, ListName, ToDoList}

    def book(): ToDoList = ToDoList.ToDoList(ListName.ListName("book"), List#{Domain.todo("write chapter")})

    @Test
    def aHubIsBuiltFromAnyFunctionOfTheRightType(): Unit \ Assert = {
        let hub = Hub.todoListHub((user, name) ->
            if (user == User.User("uberto") and name == ListName.ListName("book")) Some(book()) else None);
        Assert.assertEq(expected = Some(book()), hub#getList(User.User("uberto"), ListName.ListName("book")));
        Assert.assertEq(expected = None, hub#getList(User.User("alice"), ListName.ListName("book")))
    }

    @Test
    def theInMemoryHubFindsTheListOfTheUser(): Unit \ Assert = {
        let hub = Hub.inMemory(Map#{User.User("uberto") => List#{book()}});
        Assert.assertEq(expected = Some(book()), hub#getList(User.User("uberto"), ListName.ListName("book")));
        Assert.assertEq(expected = None, hub#getList(User.User("uberto"), ListName.ListName("missing")))
    }

}
