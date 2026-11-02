mod Logs {

    use Json.JsonTree
    use Json.JsonTree.{JNull, JInt, JString, JObject}

    /// ログの 1 行。自由な Map は持たない。項目の説明も、イベントの中身も、失敗の理由も、
    /// 入れる場所が型に無いので、書かないという規律ではなく、書けないことで守られる。
    pub enum LogRecord(String, String, String, Option[String], Option[String], Int32) with Eq, ToString

    // ---- 木への写像（書く側） ----

    def optionalString(value: Option[String]): JsonTree =
        match value {
            case Some(s) => JString(s)
            case None    => JNull
        }

    pub def toJson(record: LogRecord): JsonTree =
        match record {
            case LogRecord.LogRecord(kind, name, outcome, user, list, durationMs) =>
                JObject(
                    ("kind", JString(kind)) :: ("name", JString(name)) :: ("outcome", JString(outcome))
                    :: ("user", optionalString(user)) :: ("list", optionalString(list))
                    :: ("durationMs", JInt(durationMs)) :: Nil
                )
        }

    // ---- 木からの写像（読む側）。読めないものは、例外ではなく失敗の値にする ----

    def field(kvs: List[(String, JsonTree)], key: String): Result[String, JsonTree] =
        match List.find(kv -> match kv { case (k, _) => k == key }, kvs) {
            case Some((_, v)) => Ok(v)
            case None         => Err("missing field: ${key}")
        }

    def stringField(kvs: List[(String, JsonTree)], key: String): Result[String, String] =
        Result.flatMap(v -> match v {
            case JString(s) => Ok(s)
            case _          => Err("field ${key} is not a string")
        }, field(kvs, key))

    def optionalStringField(kvs: List[(String, JsonTree)], key: String): Result[String, Option[String]] =
        Result.flatMap(v -> match v {
            case JString(s) => Ok(Some(s))
            case JNull      => Ok(None)
            case _          => Err("field ${key} is not a string or null")
        }, field(kvs, key))

    def intField(kvs: List[(String, JsonTree)], key: String): Result[String, Int32] =
        Result.flatMap(v -> match v {
            case JInt(n) => Ok(n)
            case _       => Err("field ${key} is not an integer")
        }, field(kvs, key))

    pub def fromJson(json: JsonTree): Result[String, LogRecord] =
        match json {
            case JObject(kvs) =>
                forM (
                    kind       <- stringField(kvs, "kind");
                    name       <- stringField(kvs, "name");
                    outcome    <- stringField(kvs, "outcome");
                    user       <- optionalStringField(kvs, "user");
                    list       <- optionalStringField(kvs, "list");
                    durationMs <- intField(kvs, "durationMs")
                ) yield LogRecord.LogRecord(kind, name, outcome, user, list, durationMs)
            case _ => Err("a log line must be an object")
        }

}
