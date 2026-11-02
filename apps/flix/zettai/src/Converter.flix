mod Converter {

    use Json.JsonTree
    use Json.JsonTree.{JInt, JString}

    /// 値と木の、双方向の変換。型 a は、書く向きと読む向きの両方に現れる。
    pub type alias Converter[a] = {
        encode = a -> JsonTree,
        decode = JsonTree -> Result[String, a]
    }

    pub def string(): Converter[String] = {
        encode = s -> JString(s),
        decode = json -> match json {
            case JString(s) => Ok(s)
            case _          => Err("not a string")
        }
    }

    pub def int(): Converter[Int32] = {
        encode = n -> JInt(n),
        decode = json -> match json {
            case JInt(n) => Ok(n)
            case _       => Err("not an integer")
        }
    }

    /// 型を変えるには、行きの写像と戻りの写像の、両方が要る。
    /// 入力だけ、出力だけを写す dimap は、この束には作れない。
    pub def imap(toB: a -> b, toA: b -> a, c: Converter[a]): Converter[b] = {
        encode = value -> c#encode(toA(value)),
        decode = json -> Result.map(toB, c#decode(json))
    }

}
