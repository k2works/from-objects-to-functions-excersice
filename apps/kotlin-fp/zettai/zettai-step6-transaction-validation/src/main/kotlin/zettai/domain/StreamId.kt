package zettai.domain

/** event の列（stream）を決める。リスト名ごとに 1 つの stream を持つ。 */
data class StreamId(val user: User, val listName: ListName)
