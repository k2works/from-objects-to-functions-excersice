mod Html {

    use Domain.{ToDoItem, ToDoList}
    use Domain.ListName.ListName

    pub def escape(text: String): String =
        text
            |> String.replace(src = "&", dst = "&amp;")
            |> String.replace(src = "<", dst = "&lt;")
            |> String.replace(src = ">", dst = "&gt;")
            |> String.replace(src = "\"", dst = "&quot;")

    def renderItem(item: ToDoItem): String =
        match item { case ToDoItem.ToDoItem(description) => "<li>${escape(description)}</li>" }

    pub def render(list: ToDoList): String =
        match list {
            case ToDoList.ToDoList(ListName(name), items) =>
                let lis = items |> List.map(renderItem) |> String.intercalate("");
                "<html><body><h1>Zettai</h1><h2>${escape(name)}</h2><ul>${lis}</ul></body></html>"
        }

}
