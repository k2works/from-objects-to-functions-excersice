package zettai.adapter.postgres

import zettai.domain.ListName
import zettai.domain.User

/** `todo_list_event` の SQL。出来事を追記するだけで、UPDATE も DELETE もしない。 */
internal object EventTable {
    const val CREATE_TABLE = """
        CREATE TABLE IF NOT EXISTS todo_list_event (
            id          BIGSERIAL PRIMARY KEY,
            entity_id   TEXT        NOT NULL,
            version     INTEGER     NOT NULL,
            event_type  TEXT        NOT NULL,
            payload     JSONB       NOT NULL,
            recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            UNIQUE (entity_id, version)
        )
    """

    const val HAS_VERSION_COLUMN = """
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'todo_list_event' AND column_name = 'version'
    """

    const val CREATE_INDEX = """
        CREATE INDEX IF NOT EXISTS idx_todo_list_event_entity
            ON todo_list_event (entity_id, id)
    """

    const val INSERT_LIST_CREATED = """
        INSERT INTO todo_list_event (entity_id, version, event_type, payload)
        VALUES (?, ?, 'ListCreated', jsonb_build_object('user', ?::text, 'listName', ?::text))
    """

    const val INSERT_LIST_RENAMED = """
        INSERT INTO todo_list_event (entity_id, version, event_type, payload)
        VALUES (
            ?,
            ?,
            'ListRenamed',
            jsonb_build_object('user', ?::text, 'listName', ?::text, 'newName', ?::text)
        )
    """

    const val INSERT_ITEM_ADDED = """
        INSERT INTO todo_list_event (entity_id, version, event_type, payload)
        VALUES (
            ?,
            ?,
            'ItemAdded',
            jsonb_build_object(
                'user', ?::text,
                'listName', ?::text,
                'item', jsonb_build_object(
                    'description', ?::text,
                    'dueDate', ?::text,
                    'status', ?::text
                )
            )
        )
    """

    const val INSERT_ITEM_STATUS_CHANGED = """
        INSERT INTO todo_list_event (entity_id, version, event_type, payload)
        VALUES (
            ?,
            ?,
            'ItemStatusChanged',
            jsonb_build_object(
                'user', ?::text,
                'listName', ?::text,
                'description', ?::text,
                'newStatus', ?::text
            )
        )
    """

    private const val SELECT_COLUMNS = """
        SELECT event_type,
               version,
               payload ->> 'user' AS user_name,
               payload ->> 'listName' AS list_name,
               payload ->> 'newName' AS new_name,
               payload ->> 'description' AS description,
               payload ->> 'newStatus' AS new_status,
               payload #>> '{item,description}' AS item_description,
               payload #>> '{item,dueDate}' AS item_due_date,
               payload #>> '{item,status}' AS item_status
        FROM todo_list_event
    """

    const val SELECT_BY_ENTITY = "$SELECT_COLUMNS WHERE entity_id = ? ORDER BY id"

    /** LIKE は使わない。符号化した値に含まれる `%` がワイルドカードになるため。 */
    const val SELECT_BY_USER = "$SELECT_COLUMNS WHERE starts_with(entity_id, ?) ORDER BY id"
}

/** `/` で区切る前に、各部分の `%` と `/` を置き換える。通常の名前では `{user}/{listName}` のままになる。 */
private fun String.escapeEntityPart(): String = replace("%", "%25").replace("/", "%2F")

internal fun entityId(user: User, listName: ListName): String =
    "${userPrefix(user)}${listName.name.escapeEntityPart()}"

/** ユーザー単位で読むときの前方一致の接頭辞。 */
internal fun userPrefix(user: User): String = "${user.name.escapeEntityPart()}/"
