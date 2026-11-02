mod TestHtmlReader {

    use Domain.ToDoItem

    @Test
    def readsItemsInOrder(): Unit \ Assert =
        Assert.assertEq(
            expected = List#{Domain.todo("write chapter"), Domain.todo("publish book")},
            HtmlReader.items("<html><body><h2>book</h2><ul><li>write chapter</li><li>publish book</li></ul></body></html>")
        )

    @Test
    def restoresEscapedCharacters(): Unit \ Assert =
        Assert.assertEq(
            expected = List#{Domain.todo("read <today> & \"FP\"")},
            HtmlReader.items("<ul><li>read &lt;today&gt; &amp; &quot;FP&quot;</li></ul>")
        )

    @Test
    def readsAnEmptyListAsNoItems(): Unit \ Assert =
        Assert.assertEq(expected = Nil, HtmlReader.items("<ul></ul>"))

    @Test
    def roundTripsWhatHtmlRenders(): Unit \ Assert = {
        let items = List#{Domain.todo("a & b"), Domain.todo("<i>c</i>")};
        let list = Domain.ToDoList.ToDoList(Domain.ListName.ListName("x"), items);
        Assert.assertEq(expected = items, HtmlReader.items(Html.render(list)))
    }

}
