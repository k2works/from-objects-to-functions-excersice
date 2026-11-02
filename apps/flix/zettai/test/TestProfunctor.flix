mod TestProfunctor {

    use Json.JsonTree.{JInt, JString}

    /// 関数が等しいことを、小さな定義域のすべての点で確かめる。
    def domain(): List[Int32] = List#{Int32.minValue(), -3, -1, 0, 1, 2, 7, Int32.maxValue() / 2}

    def pre(): List[Int32 -> Int32] = List#{x -> x, x -> x / 2, x -> 0 - x, x -> x * 0 + 5}

    def post(): List[Int32 -> Int32] = List#{x -> x, x -> x / 3, x -> 0 - x, x -> x * 0 + 1}

    def base(): List[Int32 -> Int32] = List#{x -> x + 1, x -> x / 2, x -> 0 - x}

    @Test
    def dimapWithIdentitiesChangesNothing(): Unit \ Assert =
        List.forEach(f ->
            List.forEach(x -> Assert.assertEq(expected = f(x), Profunctor.dimap(a -> a, b -> b, f)(x)), domain()),
            base())

    @Test
    def dimapComposes(): Unit \ Assert =
        List.forEach(f ->
            List.forEach(l1 ->
                List.forEach(l2 ->
                    List.forEach(r1 ->
                        List.forEach(r2 ->
                            List.forEach(x -> Assert.assertEq(
                                expected = Profunctor.dimap(a -> l2(l1(a)), b -> r1(r2(b)), f)(x),
                                Profunctor.dimap(l1, r1, Profunctor.dimap(l2, r2, f))(x)), domain()),
                            post()),
                        post()),
                    pre()),
                pre()),
            base())

    @Test
    def theInputIsMappedBackwardsAndTheOutputForwards(): Unit \ Assert = {
        // 文字列を受け取り、文字列を返す関数に見せかける。入力は読み替え、出力は書き換える
        let length = Profunctor.dimap(s -> String.length(s), n -> "length=${n}", n -> n * 2);
        Assert.assertEq(expected = "length=8", length("abcd"))
    }

    /// 同じ型の値を、書き出しと読み戻しの両方向で持つ。型 a は両方に現れる。
    def stringConverter(): Converter.Converter[String] = Converter.string()

    @Test
    def aConverterNeedsBothDirectionsToChangeItsType(): Unit \ Assert = {
        // 長さ 3 の文字列を包む型。書く向きと読む向きの、2 つの写像を渡す
        let lengthOf = Converter.imap(s -> String.length(s), n -> String.repeat(n, "x"), stringConverter());
        Assert.assertEq(expected = JString("xxx"), lengthOf#encode(3));
        Assert.assertEq(expected = Ok(3), lengthOf#decode(JString("xxx")))
    }

    @Test
    def imapWithIdentitiesChangesNothing(): Unit \ Assert =
        List.forEach(text -> {
            let same = Converter.imap(s -> s, s -> s, stringConverter());
            Assert.assertEq(expected = stringConverter()#encode(text), same#encode(text));
            Assert.assertEq(expected = stringConverter()#decode(JString(text)), same#decode(JString(text)))
        }, List#{"", "a", "日本語", "a\"b"})

    @Test
    def aConverterRoundTripsThroughText(): Unit \ Assert =
        List.forEach(n -> {
            let c = Converter.int();
            Assert.assertEq(expected = Ok(n), Result.flatMap(c#decode, Json.parse(Json.render(c#encode(n)))))
        }, List#{Int32.minValue(), -1, 0, 1, Int32.maxValue()})

    @Test
    def decodingTheWrongShapeIsAFailureNotAnException(): Unit \ Assert = {
        Assert.assertEq(expected = Err("not an integer"), Converter.int()#decode(JString("1")));
        Assert.assertEq(expected = Err("not a string"), Converter.string()#decode(JInt(1)))
    }

}
