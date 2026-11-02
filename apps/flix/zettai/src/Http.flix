mod Http {

    use Domain.{User, ListName, ToDoList}
    use Hub.ToDoListHub

    pub type alias Request = { method = String, path = String }

    pub type alias Response = { status = Int32, contentType = String, body = String }

    def html(status: Int32, body: String): Response =
        { status = status, contentType = "text/html; charset=utf-8", body = body }

    def notFound(): Response =
        { status = 404, contentType = "text/plain; charset=utf-8", body = "Not Found" }

    def methodNotAllowed(): Response =
        { status = 405, contentType = "text/plain; charset=utf-8", body = "Method Not Allowed" }

    /// パスから、見たいリストの持ち主と名前を取り出す。
    def parse(path: String): Option[(User, ListName)] =
        match String.splitOn(substr = "/", path) {
            case "" :: "todo" :: user :: list :: Nil => Some((User.User(user), ListName.ListName(list)))
            case _                                   => None
        }

    def respond(found: Option[ToDoList]): Response =
        match found {
            case Some(todoList) => html(200, Html.render(todoList))
            case None           => notFound()
        }

    /// 依存を、関数値のレコード（Hub）として引数で受け取る版。
    pub def route(hub: ToDoListHub[ef], request: Request): Response \ ef =
        if (request#method != "GET")
            methodNotAllowed()
        else
            match parse(request#path) {
                case Some((user, list)) => respond(hub#getList(user, list))
                case None               => notFound()
            }

    /// 依存を、効果として型に現す版。ハンドラが無いと実行できない。
    pub def routeWithEffect(request: Request): Response \ Lookup.ToDoLists =
        if (request#method != "GET")
            methodNotAllowed()
        else
            match parse(request#path) {
                case Some((user, list)) => respond(Lookup.ToDoLists.getList(user, list))
                case None               => notFound()
            }

}
