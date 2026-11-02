mod Http {

    use Domain.{User, ListName, ToDoList}

    pub type alias Request = { method = String, path = String }

    pub type alias Response = { status = Int32, contentType = String, body = String }

    def html(status: Int32, body: String): Response =
        { status = status, contentType = "text/html; charset=utf-8", body = body }

    def notFound(): Response =
        { status = 404, contentType = "text/plain; charset=utf-8", body = "Not Found" }

    def methodNotAllowed(): Response =
        { status = 405, contentType = "text/plain; charset=utf-8", body = "Method Not Allowed" }

    pub def route(fetch: (User, ListName) -> Option[ToDoList] \ ef, request: Request): Response \ ef =
        if (request#method != "GET")
            methodNotAllowed()
        else
            match String.splitOn(substr = "/", request#path) {
                case "" :: "todo" :: user :: list :: Nil =>
                    match fetch(User.User(user), ListName.ListName(list)) {
                        case Some(todoList) => html(200, Html.render(todoList))
                        case None           => notFound()
                    }
                case _ => notFound()
            }

}
