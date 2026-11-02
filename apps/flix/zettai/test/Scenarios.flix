mod Scenarios {

    use Domain.{User, ListName, ToDoItem}
    use ZettaiActions.ZettaiActions

    /// 失敗メッセージに経路の名前が出るよう、経路名と結果を組にして比べる。
    def check(actions: ZettaiActions, expected: Option[List[ToDoItem]], actual: Option[List[ToDoItem]]): Unit \ Assert =
        Assert.assertEq(expected = (actions#name, expected), (actions#name, actual))

    pub def seeTheItemsOfAList(actions: ZettaiActions): Unit \ {Assert, IO} = {
        let found = actions#getToDoList(User.User("uberto"), ListName.ListName("book"));
        check(actions, Some(List#{
            ToDoItem.ToDoItem("write chapter"),
            ToDoItem.ToDoItem("publish book"),
            ToDoItem.ToDoItem("read <today> & \"FP\"")
        }), found)
    }

    pub def aMissingListIsNotFound(actions: ZettaiActions): Unit \ {Assert, IO} = {
        let found = actions#getToDoList(User.User("uberto"), ListName.ListName("missing"));
        check(actions, None, found)
    }

}
