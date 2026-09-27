package zettai.json

import java.time.LocalDate
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoStatus
import zettai.domain.User
import zettai.domain.events.ItemAdded
import zettai.domain.events.ItemStatusChanged
import zettai.domain.events.ListCreated
import zettai.domain.events.ListRenamed
import zettai.domain.events.ToDoListEvent
import zettai.fp.Converter
import zettai.fp.Outcome
import zettai.fp.Success
import zettai.fp.ZettaiError
import zettai.fp.ZettaiParsingError

/**
 * 出来事と JSON の双方向変換。
 *
 * 第 9 章では toJson と eventFrom の 2 つの関数に分かれていた。
 * 片方だけ直して壊れることがありえたので、1 つの型にまとめた。
 */
val eventConverter: Converter<ToDoListEvent, String> =
    Converter<ToDoListEvent, JsonObject>(
        render = ::toJsonObject,
        parse = ::fromJsonObject
    ).map(to = JsonObject::toJsonString, from = String::toJsonObject)

private fun toJsonObject(event: ToDoListEvent): JsonObject =
    when (event) {
        is ListCreated -> base(event)

        is ItemAdded -> JsonObject(
            base(event).fields + mapOf(
                "description" to event.item.description,
                "dueDate" to event.item.dueDate?.toString(),
                "status" to event.item.status.toString()
            )
        )

        is ListRenamed -> JsonObject(base(event).fields + mapOf("newName" to event.newName.name))

        is ItemStatusChanged -> JsonObject(
            base(event).fields + mapOf(
                "description" to event.description,
                "newStatus" to event.newStatus.toString()
            )
        )
    }

private fun base(event: ToDoListEvent): JsonObject =
    JsonObject.of(
        "eventType" to event::class.simpleName,
        "user" to event.user.name,
        "listName" to event.listName.name
    )

private fun fromJsonObject(json: JsonObject): Outcome<ZettaiError, ToDoListEvent> {
    val user = User(json.text("user"))
    val listName = ListName(json.text("listName"))

    return when (val type = json.textOrNull("eventType")) {
        "ListCreated" -> Success(ListCreated(user, listName))

        "ItemAdded" -> Success(
            ItemAdded(
                user,
                listName,
                ToDoItem(
                    description = json.text("description"),
                    dueDate = json.textOrNull("dueDate")?.let(LocalDate::parse),
                    status = ToDoStatus.valueOf(json.text("status"))
                )
            )
        )

        "ListRenamed" -> Success(ListRenamed(user, listName, ListName(json.text("newName"))))

        "ItemStatusChanged" -> Success(
            ItemStatusChanged(
                user,
                listName,
                json.text("description"),
                ToDoStatus.valueOf(json.text("newStatus"))
            )
        )

        else -> zettai.fp.Failure(ZettaiParsingError("知らない出来事の種類です: $type"))
    }
}
