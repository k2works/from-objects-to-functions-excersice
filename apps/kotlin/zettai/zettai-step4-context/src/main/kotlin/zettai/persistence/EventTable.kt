package zettai.persistence

/**
 * イベントストアのスキーマ。
 *
 * 追記のみで UPDATE も DELETE もしないので、テーブルは 1 つだけ。
 * 詳細は docs/design/data-model.md を参照。
 */
object EventTable {
    const val NAME = "todo_list_event"

    val CREATE = """
        CREATE TABLE IF NOT EXISTS $NAME (
            id          BIGSERIAL PRIMARY KEY,
            entity_id   TEXT        NOT NULL,
            event_type  TEXT        NOT NULL,
            payload     JSONB       NOT NULL,
            recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
        )
    """.trimIndent()

    val CREATE_INDEX = """
        CREATE INDEX IF NOT EXISTS idx_${NAME}_entity ON $NAME (entity_id, id)
    """.trimIndent()

    const val INSERT = "INSERT INTO $NAME (entity_id, event_type, payload) VALUES (?, ?, ?::jsonb)"

    const val SELECT_ALL = "SELECT event_type, payload FROM $NAME ORDER BY id"
}
