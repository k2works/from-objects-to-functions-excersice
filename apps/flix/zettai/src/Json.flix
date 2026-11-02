mod Json {

    import java.lang.Character

    /// 文字列を手で組み立てずに、木として持つ。
    pub enum JsonTree with Eq, ToString {
        case JNull
        case JBool(Bool)
        case JInt(Int32)
        case JString(String)
        case JArray(List[JsonTree])
        case JObject(List[(String, JsonTree)])
    }

    // ---- 木から文字列へ ----

    def hexDigit(n: Int32): String =
        match List.nth(n, String.toList("0123456789abcdef")) {
            case Some(c) => ToString.toString(c)
            case None    => "0"
        }

    def unicodeEscape(code: Int32): String =
        "\\u00${hexDigit(code / 16)}${hexDigit(code - (code / 16) * 16)}"

    def escapeChar(c: Char): String =
        match c {
            case '"'  => "\\\""
            case '\\' => "\\\\"
            case '\n' => "\\n"
            case '\t' => "\\t"
            case '\r' => "\\r"
            case _    =>
                let code = Character.hashCode(c);
                if (code < 32) unicodeEscape(code) else ToString.toString(c)
        }

    def renderString(s: String): String =
        "\"" + String.intercalate("", List.map(escapeChar, String.toList(s))) + "\""

    pub def render(json: JsonTree): String =
        match json {
            case JsonTree.JNull        => "null"
            case JsonTree.JBool(true)  => "true"
            case JsonTree.JBool(false) => "false"
            case JsonTree.JInt(n)      => Int32.toString(n)
            case JsonTree.JString(s)   => renderString(s)
            case JsonTree.JArray(xs)   => "[" + String.intercalate(",", List.map(render, xs)) + "]"
            case JsonTree.JObject(kvs) =>
                "{" + String.intercalate(",", List.map(kv -> match kv { case (k, v) => renderString(k) + ":" + render(v) }, kvs)) + "}"
        }

    // ---- 文字列から木へ ----

    type alias Parsed[a] = Result[String, (a, List[Char])]

    def skipSpaces(cs: List[Char]): List[Char] = List.dropWhile(Char.isWhitespace, cs)

    def hexValue(c: Char): Option[Int32] =
        match c {
            case '0' => Some(0)  case '1' => Some(1)  case '2' => Some(2)  case '3' => Some(3)
            case '4' => Some(4)  case '5' => Some(5)  case '6' => Some(6)  case '7' => Some(7)
            case '8' => Some(8)  case '9' => Some(9)  case 'a' => Some(10) case 'b' => Some(11)
            case 'c' => Some(12) case 'd' => Some(13) case 'e' => Some(14) case 'f' => Some(15)
            case 'A' => Some(10) case 'B' => Some(11) case 'C' => Some(12) case 'D' => Some(13)
            case 'E' => Some(14) case 'F' => Some(15)
            case _   => None
        }

    /// 引用符の直後から、閉じる引用符までを読む。acc は、読んだ文字を逆順に持つ。
    def parseStringBody(cs: List[Char], acc: List[String]): Parsed[String] =
        match cs {
            case '"' :: rest => Ok((String.intercalate("", List.reverse(acc)), rest))
            case '\\' :: e :: rest =>
                match e {
                    case '"'  => parseStringBody(rest, "\"" :: acc)
                    case '\\' => parseStringBody(rest, "\\" :: acc)
                    case '/'  => parseStringBody(rest, "/" :: acc)
                    case 'n'  => parseStringBody(rest, "\n" :: acc)
                    case 't'  => parseStringBody(rest, "\t" :: acc)
                    case 'r'  => parseStringBody(rest, "\r" :: acc)
                    case 'b'  => parseStringBody(rest, Character.toString(8) :: acc)
                    case 'f'  => parseStringBody(rest, Character.toString(12) :: acc)
                    case 'u'  =>
                        match rest {
                            case a :: b :: c :: d :: tail =>
                                match (hexValue(a), hexValue(b), hexValue(c), hexValue(d)) {
                                    case (Some(w), Some(x), Some(y), Some(z)) =>
                                        parseStringBody(tail, Character.toString(((w * 16 + x) * 16 + y) * 16 + z) :: acc)
                                    case _ => Err("bad unicode escape")
                                }
                            case _ => Err("bad unicode escape")
                        }
                    case _ => Err("bad escape")
                }
            case c :: rest => parseStringBody(rest, ToString.toString(c) :: acc)
            case Nil       => Err("unterminated string")
        }

    def parseInt(cs: List[Char]): Parsed[JsonTree] = {
        let negative = match cs { case '-' :: _ => true, case _ => false };
        let body = if (negative) List.drop(1, cs) else cs;
        let digits = List.takeWhile(Char.isDigit, body);
        let rest = List.dropWhile(Char.isDigit, body);
        if (List.isEmpty(digits))
            Err("expected digits")
        else
            match Int32.fromString(String.intercalate("", List.map(ToString.toString, digits))) {
                case Some(n) => Ok((JsonTree.JInt(if (negative) 0 - n else n), rest))
                case None    => Err("number out of range")
            }
    }

    def parseValue(input: List[Char]): Parsed[JsonTree] =
        match skipSpaces(input) {
            case 'n' :: 'u' :: 'l' :: 'l' :: rest      => Ok((JsonTree.JNull, rest))
            case 't' :: 'r' :: 'u' :: 'e' :: rest      => Ok((JsonTree.JBool(true), rest))
            case 'f' :: 'a' :: 'l' :: 's' :: 'e' :: rest => Ok((JsonTree.JBool(false), rest))
            case '"' :: rest =>
                match parseStringBody(rest, Nil) {
                    case Ok((s, tail)) => Ok((JsonTree.JString(s), tail))
                    case Err(e)        => Err(e)
                }
            case '[' :: rest => parseArray(skipSpaces(rest), Nil)
            case '{' :: rest => parseObject(skipSpaces(rest), Nil)
            case c :: rest   =>
                if (c == '-' or Char.isDigit(c)) parseInt(c :: rest) else Err("unexpected character")
            case Nil         => Err("unexpected end")
        }

    def parseArray(cs: List[Char], acc: List[JsonTree]): Parsed[JsonTree] =
        match cs {
            case ']' :: rest => if (List.isEmpty(acc)) Ok((JsonTree.JArray(Nil), rest)) else Err("trailing comma")
            case _ =>
                match parseValue(cs) {
                    case Err(e)           => Err(e)
                    case Ok((v, tail))    =>
                        match skipSpaces(tail) {
                            case ',' :: more => parseArray(skipSpaces(more), v :: acc)
                            case ']' :: more => Ok((JsonTree.JArray(List.reverse(v :: acc)), more))
                            case _           => Err("expected , or ]")
                        }
                }
        }

    def parseObject(cs: List[Char], acc: List[(String, JsonTree)]): Parsed[JsonTree] =
        match cs {
            case '}' :: rest => if (List.isEmpty(acc)) Ok((JsonTree.JObject(Nil), rest)) else Err("trailing comma")
            case '"' :: rest =>
                match parseStringBody(rest, Nil) {
                    case Err(e)         => Err(e)
                    case Ok((key, tail)) =>
                        match skipSpaces(tail) {
                            case ':' :: more =>
                                match parseValue(more) {
                                    case Err(e)        => Err(e)
                                    case Ok((v, after)) =>
                                        match skipSpaces(after) {
                                            case ',' :: next => parseObject(skipSpaces(next), (key, v) :: acc)
                                            case '}' :: next => Ok((JsonTree.JObject(List.reverse((key, v) :: acc)), next))
                                            case _           => Err("expected , or }")
                                        }
                                }
                            case _ => Err("expected :")
                        }
                }
            case _ => Err("expected a key")
        }

    /// 文字列を木に読む。木のあとに文字が残っていれば、失敗にする。
    pub def parse(text: String): Result[String, JsonTree] =
        match parseValue(String.toList(text)) {
            case Err(e)         => Err(e)
            case Ok((json, rest)) =>
                if (List.isEmpty(skipSpaces(rest))) Ok(json) else Err("unexpected trailing characters")
        }

}
