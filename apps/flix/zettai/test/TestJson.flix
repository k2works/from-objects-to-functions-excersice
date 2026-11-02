mod TestJson {

    use Json.JsonTree.{JNull, JBool, JInt, JString, JArray, JObject}

    def leaves(): List[Json.JsonTree] = List#{
        JNull, JBool(true), JBool(false), JInt(0), JInt(-12), JInt(Int32.minValue()), JInt(Int32.maxValue()), JString(""),
        JString("a\"b\\c/d"), JString("line\nbreak\ttab"), JString("ctrl\u0007end"), JString("日本語")
    }

    /// 葉のほか、葉を 0 〜 2 個持つ配列と、キー "a", "b" の対を持つオブジェクト。
    def trees(): List[Json.JsonTree] = {
        let one = leaves();
        let arrays = List#{JArray(Nil)} ::: List.map(x -> JArray(x :: Nil), one)
            ::: List.flatMap(x -> List.map(y -> JArray(x :: y :: Nil), one), one);
        let objects = List#{JObject(Nil)} ::: List.map(x -> JObject(("a", x) :: Nil), one)
            ::: List.flatMap(x -> List.map(y -> JObject(("a", x) :: ("b", y) :: Nil), one), one);
        let nested = List.map(a -> JArray(a :: Nil), arrays) ::: List.map(o -> JObject(("k", o) :: Nil), objects);
        one ::: arrays ::: objects ::: nested
    }

    @Test
    def rendersWithoutSpaces(): Unit \ Assert =
        Assert.assertEq(
            expected = "{\"a\":1,\"b\":[true,null]}",
            Json.render(JObject(("a", JInt(1)) :: ("b", JArray(JBool(true) :: JNull :: Nil)) :: Nil))
        )

    @Test
    def escapesQuotesBackslashesAndControlCharacters(): Unit \ Assert = {
        Assert.assertEq(expected = "\"a\\\"b\\\\c\"", Json.render(JString("a\"b\\c")));
        Assert.assertEq(expected = "\"x\\ny\\tz\"", Json.render(JString("x\ny\tz")));
        Assert.assertEq(expected = "\"\\u0007\"", Json.render(JString("\u0007")))
    }

    @Test
    def parsesWhatItIsGiven(): Unit \ Assert =
        Assert.assertEq(
            expected = Ok(JObject(("a", JInt(1)) :: ("b", JArray(JBool(true) :: JNull :: Nil)) :: Nil)),
            Json.parse(" { \"a\" : 1 , \"b\" : [ true , null ] } ")
        )

    @Test
    def rejectsTextThatIsNotJson(): Unit \ Assert = {
        List.forEach(text -> match Json.parse(text) {
            case Err(_) => ()
            case Ok(_)  => Assert.assertEq(expected = "rejected: ${text}", "accepted: ${text}")
        }, List#{"", "{", "[1,", "{\"a\"}", "tru", "\"unterminated", "1 2", "[1 2]", "{\"a\":}", "\"\\q\""})
    }

    @Test
    def parsingWhatWasRenderedGivesBackTheSameTree(): Unit \ Assert = {
        List.forEach(tree -> Assert.assertEq(expected = Ok(tree), Json.parse(Json.render(tree))), trees())
    }

    @Test
    def theTreesCoverEveryShape(): Unit \ Assert =
        Assert.assertEq(expected = 640, List.length(trees()))

}
