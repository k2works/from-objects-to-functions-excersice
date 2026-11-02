mod TestErrorMapping {

    use Domain.{User, ListName, ToDoStatus}
    use Errors.ZettaiError

    def uberto(): User = User.User("uberto")

    def book(): ListName = ListName.ListName("book")

    @Test
    def aMissingListOrItemIsNotFound(): Unit \ Assert = {
        Assert.assertEq(expected = 404, Http.statusOf(ZettaiError.ListNotFound(uberto(), book())));
        Assert.assertEq(expected = 404, Http.statusOf(ZettaiError.ItemNotFound(uberto(), book(), "x")))
    }

    @Test
    def aRequestTheDomainRefusesIsABadRequest(): Unit \ Assert = {
        Assert.assertEq(expected = 400, Http.statusOf(ZettaiError.ListAlreadyExists(uberto(), book())));
        Assert.assertEq(expected = 400, Http.statusOf(ZettaiError.InvalidTransition("x", ToDoStatus.Todo, ToDoStatus.Blocked)))
    }

}
