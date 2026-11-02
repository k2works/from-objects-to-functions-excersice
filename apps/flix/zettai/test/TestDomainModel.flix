mod TestDomainModel {

    use Domain.{ToDoItem, ToDoStatus}

    @Test
    def aNewItemStartsAsTodo(): Unit \ Assert =
        Assert.assertEq(
            expected = ToDoItem.ToDoItem("write chapter", ToDoStatus.Todo),
            Domain.todo("write chapter")
        )

    @Test
    def statusesAreAFiniteSet(): Unit \ Assert =
        Assert.assertEq(
            expected = List#{ToDoStatus.Todo, ToDoStatus.InProgress, ToDoStatus.Done, ToDoStatus.Blocked},
            Domain.allStatuses()
        )

}
