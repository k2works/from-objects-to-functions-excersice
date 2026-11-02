mod TestFailureBody {

    use Domain.{User, ListName}
    use Errors.ZettaiError
    use Http.Request

    def get(path: String): Request = { method = "GET", path = path, body = "" }

    /// 保存の失敗の理由には、SQL 文・テーブル名・接続先などの内部情報が入りうる。
    def leakyStorage(): Hub.ToDoListHub[{}] =
        Hub.todoListHub((_user, _name) -> Err(ZettaiError.StorageFailure("password=hunter2 host=db.internal insert into events_x")))

    @Test
    def aStorageFailureAnswers500WithoutItsReason(): Unit \ Assert = {
        let response = Http.route(leakyStorage(), get("/todo/uberto/book"));
        Assert.assertEq(expected = 500, response#status);
        Assert.assertFalse(String.contains(substr = "hunter2", response#body));
        Assert.assertFalse(String.contains(substr = "db.internal", response#body));
        Assert.assertFalse(String.contains(substr = "insert into", response#body))
    }

    @Test
    def aRefusalTheCallerCanActOnKeepsItsDetail(): Unit \ Assert = {
        let missing = Hub.todoListHub((user, name) -> Err(ZettaiError.ListNotFound(user, name)));
        let response = Http.route(missing, get("/todo/uberto/book"));
        Assert.assertEq(expected = 404, response#status);
        Assert.assertTrue(String.contains(substr = "ListNotFound", response#body))
    }

}
