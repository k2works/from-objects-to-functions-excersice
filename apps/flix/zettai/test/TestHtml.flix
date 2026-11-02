mod TestHtml {

    use Domain.{ListName, ToDoItem, ToDoList}

    @Test
    def rendersTheListNameAndItems(): Unit \ Assert = {
        let list = ToDoList.ToDoList(
            ListName.ListName("book"),
            List#{Domain.todo("write chapter"), Domain.todo("publish book")}
        );
        let html = Html.render(list);
        Assert.assertTrue(String.contains(substr = "<h2>book</h2>", html));
        Assert.assertTrue(String.contains(substr = "<li>write chapter</li>", html));
        Assert.assertTrue(String.contains(substr = "<li>publish book</li>", html))
    }

    @Test
    def escapesDomainValuesSoTheyAreNotMarkup(): Unit \ Assert = {
        let list = ToDoList.ToDoList(
            ListName.ListName("<b>x</b>"),
            List#{Domain.todo("read <today> & \"FP\"")}
        );
        let html = Html.render(list);
        Assert.assertTrue(String.contains(substr = "<h2>&lt;b&gt;x&lt;/b&gt;</h2>", html));
        Assert.assertTrue(String.contains(substr = "<li>read &lt;today&gt; &amp; &quot;FP&quot;</li>", html))
    }

}
