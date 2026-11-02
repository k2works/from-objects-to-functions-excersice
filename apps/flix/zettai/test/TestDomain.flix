mod TestDomain {

    use Domain.{User, ListName, ToDoItem, ToDoList}

    def lists(): Map[User, List[ToDoList]] =
        Map#{
            User.User("uberto") => List#{
                ToDoList.ToDoList(
                    ListName.ListName("book"),
                    List#{Domain.todo("write chapter"), Domain.todo("publish book")}
                )
            }
        }

    @Test
    def findsTheListOfTheUser(): Unit \ Assert =
        Assert.assertEq(
            expected = Some(ToDoList.ToDoList(
                ListName.ListName("book"),
                List#{Domain.todo("write chapter"), Domain.todo("publish book")}
            )),
            Domain.findToDoList(lists(), User.User("uberto"), ListName.ListName("book"))
        )

    @Test
    def returnsNoneWhenTheListDoesNotExist(): Unit \ Assert =
        Assert.assertEq(
            expected = None,
            Domain.findToDoList(lists(), User.User("uberto"), ListName.ListName("missing"))
        )

    @Test
    def returnsNoneWhenTheUserDoesNotExist(): Unit \ Assert =
        Assert.assertEq(
            expected = None,
            Domain.findToDoList(lists(), User.User("nobody"), ListName.ListName("book"))
        )

}
