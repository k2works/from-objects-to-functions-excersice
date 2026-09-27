package zettai.json

/**
 * 入れ子のない JSON。
 *
 * 第 9 章では文字列を正規表現で読んでいた。Converter で双方向にするために、
 * いったん「名前と値の並び」という形を経由する。
 */
data class JsonObject(val fields: Map<String, String?>) {
    fun text(name: String): String = fields[name] ?: error("$name が見つかりません")

    fun textOrNull(name: String): String? = fields[name]

    companion object {
        fun of(vararg pairs: Pair<String, String?>) = JsonObject(pairs.toMap())
    }
}

/** JSON の文字列に書き出す。 */
fun JsonObject.toJsonString(): String =
    fields.entries.joinToString(",", prefix = "{", postfix = "}") { (name, value) ->
        if (value == null) """"$name":null""" else """"$name":"$value""""
    }

/**
 * JSON の文字列を読む。
 *
 * コロンの前後の空白を許している。PostgreSQL の JSONB は保存時に JSON を
 * 正規化するため、書き込んだ文字列とそのまま同じものは返ってこない。
 * 第 9 章で実際にこれで結合テストが落ちた。
 */
fun String.toJsonObject(): JsonObject {
    val fields = FIELD.findAll(this).associate { match ->
        val (name, quoted, nullLiteral) = match.destructured

        name to if (nullLiteral.isNotEmpty()) null else quoted
    }

    return JsonObject(fields)
}

private val FIELD = Regex(""""(\w+)"\s*:\s*(?:"([^"]*)"|(null))""")
