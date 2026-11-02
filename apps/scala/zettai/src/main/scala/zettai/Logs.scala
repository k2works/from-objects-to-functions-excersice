package zettai

import zettai.JsonTree.*

/**
 * ログの 1 行。自由な Map は持たない。項目の説明も、イベントの中身も、失敗の理由も、
 * 入れる場所が型に無いので、書かないという規律ではなく、書けないことで守られる。
 */
final case class LogRecord(
    kind: String,
    name: String,
    outcome: String,
    user: Option[String],
    list: Option[String],
    durationMs: Int
)

object Logs:

  // ---- 木への写像（書く側） ----

  private def optionalString(value: Option[String]): JsonTree =
    value.fold(JNull)(JString(_))

  def toJson(record: LogRecord): JsonTree =
    JObject(
      List(
        "kind"       -> JString(record.kind),
        "name"       -> JString(record.name),
        "outcome"    -> JString(record.outcome),
        "user"       -> optionalString(record.user),
        "list"       -> optionalString(record.list),
        "durationMs" -> JInt(record.durationMs)
      )
    )

  // ---- 木からの写像（読む側）。読めないものは、例外ではなく失敗の値にする ----

  private def field(kvs: List[(String, JsonTree)], key: String): Either[String, JsonTree] =
    kvs.find(_._1 == key).map(_._2).toRight(s"missing field: $key")

  private def stringField(kvs: List[(String, JsonTree)], key: String): Either[String, String] =
    field(kvs, key).flatMap {
      case JString(s) => Right(s)
      case _          => Left(s"field $key is not a string")
    }

  private def optionalStringField(kvs: List[(String, JsonTree)], key: String): Either[String, Option[String]] =
    field(kvs, key).flatMap {
      case JString(s) => Right(Some(s))
      case JNull      => Right(None)
      case _          => Left(s"field $key is not a string or null")
    }

  private def intField(kvs: List[(String, JsonTree)], key: String): Either[String, Int] =
    field(kvs, key).flatMap {
      case JInt(n) => Right(n)
      case _       => Left(s"field $key is not an integer")
    }

  def fromJson(json: JsonTree): Either[String, LogRecord] =
    json match
      case JObject(kvs) =>
        for
          kind       <- stringField(kvs, "kind")
          name       <- stringField(kvs, "name")
          outcome    <- stringField(kvs, "outcome")
          user       <- optionalStringField(kvs, "user")
          list       <- optionalStringField(kvs, "list")
          durationMs <- intField(kvs, "durationMs")
        yield LogRecord(kind, name, outcome, user, list, durationMs)
      case _ => Left("a log line must be an object")
