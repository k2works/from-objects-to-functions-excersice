mod TestLogRecord {

    use Json.JsonTree.{JNull, JInt, JString, JObject}
    use Logs.LogRecord

    def records(): List[LogRecord] = {
        let kinds = List#{"hub", "command", "store", "http"};
        let outcomes = List#{"ok", "ListNotFound", "StorageFailure"};
        let users = List#{None, Some("uberto"), Some("日本語")};
        let durations = List#{0, 7, 2147483647};
        List.flatMap(kind ->
            List.flatMap(outcome ->
                List.flatMap(user ->
                    List.map(ms -> Logs.LogRecord.LogRecord(kind, "op", outcome, user, user, ms), durations),
                    users),
                outcomes),
            kinds)
    }

    @Test
    def rendersEveryFieldAndOmitsNothing(): Unit \ Assert =
        Assert.assertEq(
            expected = "{\"kind\":\"hub\",\"name\":\"getList\",\"outcome\":\"ok\",\"user\":\"uberto\",\"list\":null,\"durationMs\":12}",
            Json.render(Logs.toJson(Logs.LogRecord.LogRecord("hub", "getList", "ok", Some("uberto"), None, 12)))
        )

    @Test
    def decodingWhatWasEncodedGivesBackTheRecord(): Unit \ Assert =
        List.forEach(record -> Assert.assertEq(expected = Ok(record), Logs.fromJson(Logs.toJson(record))), records())

    @Test
    def aLineWrittenAsTextCanBeReadBack(): Unit \ Assert =
        List.forEach(record ->
            Assert.assertEq(
                expected = Ok(record),
                Result.flatMap(Logs.fromJson, Json.parse(Json.render(Logs.toJson(record))))),
            records())

    @Test
    def anObjectMissingAFieldIsRejected(): Unit \ Assert =
        Assert.assertEq(
            expected = Err("missing field: outcome"),
            Logs.fromJson(JObject(("kind", JString("hub")) :: ("name", JString("x")) :: Nil))
        )

    @Test
    def aFieldOfTheWrongTypeIsRejected(): Unit \ Assert =
        Assert.assertEq(
            expected = Err("field durationMs is not an integer"),
            Logs.fromJson(JObject(
                ("kind", JString("hub")) :: ("name", JString("x")) :: ("outcome", JString("ok"))
                :: ("user", JNull) :: ("list", JNull) :: ("durationMs", JString("12")) :: Nil))
        )

    @Test
    def thereIsNoFieldForFreeFormContent(): Unit \ Assert = {
        // 項目の説明や理由を入れる場所が、型に無い。出力に現れるキーは固定の 6 つだけ。
        let keys = match Logs.toJson(Logs.LogRecord.LogRecord("a", "b", "c", None, None, 0)) {
            case JObject(kvs) => List.map(kv -> match kv { case (k, _) => k }, kvs)
            case _            => Nil
        };
        Assert.assertEq(expected = List#{"kind", "name", "outcome", "user", "list", "durationMs"}, keys)
    }

}
