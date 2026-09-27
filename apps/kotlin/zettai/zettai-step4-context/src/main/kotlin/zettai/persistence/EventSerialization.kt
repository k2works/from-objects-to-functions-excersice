package zettai.persistence

import java.time.LocalDate
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoStatus
import zettai.domain.User
import zettai.domain.events.ItemAdded
import zettai.domain.events.ItemStatusChanged
import zettai.domain.events.ListCreated
import zettai.domain.events.ToDoListEvent

/**
 * 出来事と JSON の相互変換。
 *
 * 手書きなのは、第 12 章で Kondor（関数型の JSON ライブラリ）に置き換えるため。
 * ここで既製のライブラリを入れると、第 12 章で説明することが無くなる。
 */
fun ToDoListEvent.toJson(): String =
    when (this) {
        is ListCreated -> """{"user":"${user.name}","listName":"${listName.name}"}"""

        is ItemAdded ->
            """{"user":"${user.name}","listName":"${listName.name}","description":"${item.description}",""" +
                """"dueDate":${item.dueDate.toJsonValue()},"status":"${item.status}"}"""

        is ItemStatusChanged ->
            """{"user":"${user.name}","listName":"${listName.name}",""" +
                """"description":"$description","newStatus":"$newStatus"}"""
    }

fun eventFrom(eventType: String, json: String): ToDoListEvent {
    val fields = json.parseFlatJson()
    val user = User(fields.getValue("user"))
    val listName = ListName(fields.getValue("listName"))

    return when (eventType) {
        "ListCreated" -> ListCreated(user, listName)

        "ItemAdded" -> ItemAdded(
            user,
            listName,
            ToDoItem(
                description = fields.getValue("description"),
                dueDate = fields["dueDate"]?.let(LocalDate::parse),
                status = ToDoStatus.valueOf(fields.getValue("status"))
            )
        )

        "ItemStatusChanged" -> ItemStatusChanged(
            user,
            listName,
            fields.getValue("description"),
            ToDoStatus.valueOf(fields.getValue("newStatus"))
        )

        else -> error("知らない出来事の種類です: $eventType")
    }
}

private fun LocalDate?.toJsonValue(): String = if (this == null) "null" else "\"$this\""

/**
 * 入れ子のない JSON のフィールドを拾う。
 *
 * コロンの前後の空白を許している。PostgreSQL の JSONB は保存時に JSON を
 * 正規化するため、書き込んだ文字列とそのまま同じものは返ってこない。
 * 実際にこれで結合テストが落ちた。
 */
private val FIELD = Regex(""""(\w+)"\s*:\s*(?:"([^"]*)"|(null))""")

/** 入れ子のない JSON だけを読む。第 12 章で Kondor に置き換える。 */
private fun String.parseFlatJson(): Map<String, String> =
    FIELD.findAll(this)
        .mapNotNull { match ->
            val (name, quoted, nullLiteral) = match.destructured

            if (nullLiteral.isNotEmpty()) null else name to quoted
        }
        .toMap()
