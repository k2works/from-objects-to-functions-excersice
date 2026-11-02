mod HtmlReader {

    use Domain.ToDoItem

    def unescape(text: String): String =
        text
            |> String.replace(src = "&lt;", dst = "<")
            |> String.replace(src = "&gt;", dst = ">")
            |> String.replace(src = "&quot;", dst = "\"")
            |> String.replace(src = "&amp;", dst = "&")

    def itemOf(fragment: String): Option[ToDoItem] =
        match String.splitOn(substr = "</li>", fragment) {
            case description :: _ :: Nil => Some(ToDoItem.ToDoItem(unescape(description)))
            case _                       => None
        }

    pub def items(html: String): List[ToDoItem] =
        match String.splitOn(substr = "<li>", html) {
            case _ :: fragments => List.filterMap(itemOf, fragments)
            case Nil            => Nil
        }

}
