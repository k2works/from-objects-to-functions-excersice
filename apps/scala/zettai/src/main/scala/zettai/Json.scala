package zettai

/** JSON の木。 */
enum JsonTree:
  case JNull
  case JInt(value: Int)
  case JString(value: String)
  case JArray(items: List[JsonTree])
  case JObject(fields: List[(String, JsonTree)])

/** 標準に JSON が無いので自作する。木から文字列、文字列から木への、双方向の変換。読めないものは例外ではなく失敗の値にする。 */
object Json:

  import JsonTree.*

  // ---- 木から文字列（書く側） ----

  private def escape(text: String): String =
    val out = new StringBuilder("\"")
    text.foreach {
      case '"'  => out.append("\\\"")
      case '\\' => out.append("\\\\")
      case '\n' => out.append("\\n")
      case '\r' => out.append("\\r")
      case '\t' => out.append("\\t")
      case c if c < ' ' => out.append("\\u%04x".format(c.toInt))
      case c            => out.append(c)
    }
    out.append('"').toString

  def render(tree: JsonTree): String =
    tree match
      case JNull          => "null"
      case JInt(n)        => n.toString
      case JString(s)     => escape(s)
      case JArray(items)  => items.map(render).mkString("[", ",", "]")
      case JObject(kvs)   => kvs.map((k, v) => s"${escape(k)}:${render(v)}").mkString("{", ",", "}")

  // ---- 文字列から木（読む側） ----

  private type Parsed[A] = Either[String, (A, Int)]

  private def skipSpaces(text: String, from: Int): Int =
    var i = from
    while i < text.length && text(i).isWhitespace do i += 1
    i

  private def expect(text: String, at: Int, literal: String): Either[String, Int] =
    if text.startsWith(literal, at) then Right(at + literal.length) else Left(s"expected $literal at $at")

  private def parseString(text: String, from: Int): Parsed[String] =
    if from >= text.length || text(from) != '"' then Left(s"expected string at $from")
    else
      val out = new StringBuilder
      var i   = from + 1
      var result: Option[Parsed[String]] = None
      while result.isEmpty do
        if i >= text.length then result = Some(Left("unterminated string"))
        else
          text(i) match
            case '"' => result = Some(Right((out.toString, i + 1)))
            case '\\' =>
              if i + 1 >= text.length then result = Some(Left("unterminated escape"))
              else
                text(i + 1) match
                  case '"'  => out.append('"'); i += 2
                  case '\\' => out.append('\\'); i += 2
                  case '/'  => out.append('/'); i += 2
                  case 'n'  => out.append('\n'); i += 2
                  case 'r'  => out.append('\r'); i += 2
                  case 't'  => out.append('\t'); i += 2
                  case 'u' =>
                    val hex = text.slice(i + 2, i + 6)
                    if hex.length == 4 && hex.forall(c => Character.digit(c, 16) >= 0) then
                      out.append(Integer.parseInt(hex, 16).toChar); i += 6
                    else result = Some(Left(s"bad unicode escape at $i"))
                  case other => result = Some(Left(s"bad escape \\$other at $i"))
            case c => out.append(c); i += 1
      result.get

  /** 符号つきで読み、範囲を確かめる。Int の最小値は、桁だけを先に Int に読むと読めない。 */
  private def parseInt(text: String, from: Int): Parsed[JsonTree] =
    var i = from
    if i < text.length && text(i) == '-' then i += 1
    val digitsStart = i
    while i < text.length && text(i).isDigit do i += 1
    if i == digitsStart then Left(s"expected number at $from")
    else
      val value = BigInt(text.substring(from, i))
      if value.isValidInt then Right((JInt(value.toInt), i)) else Left(s"integer out of range at $from")

  private def parseValue(text: String, from: Int): Parsed[JsonTree] =
    val at = skipSpaces(text, from)
    if at >= text.length then Left("unexpected end")
    else
      text(at) match
        case 'n' => expect(text, at, "null").map(next => (JNull, next))
        case '"' => parseString(text, at).map((s, next) => (JString(s), next))
        case '[' => parseArray(text, at + 1, Nil)
        case '{' => parseObject(text, at + 1, Nil)
        case c if c == '-' || c.isDigit => parseInt(text, at)
        case other => Left(s"unexpected $other at $at")

  private def parseArray(text: String, from: Int, acc: List[JsonTree]): Parsed[JsonTree] =
    val at = skipSpaces(text, from)
    if acc.isEmpty && at < text.length && text(at) == ']' then Right((JArray(Nil), at + 1))
    else
      parseValue(text, at).flatMap { (item, next) =>
        val after = skipSpaces(text, next)
        if after >= text.length then Left("unterminated array")
        else
          text(after) match
            case ',' => parseArray(text, after + 1, item :: acc)
            case ']' => Right((JArray((item :: acc).reverse), after + 1))
            case _   => Left(s"expected , or ] at $after")
      }

  private def parseObject(text: String, from: Int, acc: List[(String, JsonTree)]): Parsed[JsonTree] =
    val at = skipSpaces(text, from)
    if at < text.length && text(at) == '}' && acc.isEmpty then Right((JObject(Nil), at + 1))
    else
      for
        key   <- parseString(text, at)
        colon <- Right(skipSpaces(text, key._2))
        _     <- if colon < text.length && text(colon) == ':' then Right(()) else Left(s"expected : at $colon")
        value <- parseValue(text, colon + 1)
        after  = skipSpaces(text, value._2)
        entry  = (key._1, value._1)
        result <-
          if after >= text.length then Left("unterminated object")
          else
            text(after) match
              case ',' => parseObject(text, after + 1, entry :: acc)
              case '}' => Right((JObject((entry :: acc).reverse), after + 1))
              case _   => Left(s"expected , or } at $after")
      yield result

  def parse(text: String): Either[String, JsonTree] =
    parseValue(text, 0).flatMap { (tree, next) =>
      if skipSpaces(text, next) == text.length then Right(tree) else Left(s"unexpected trailing characters at $next")
    }
