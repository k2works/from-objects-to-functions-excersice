package zettai.persistence

import zettai.domain.ListName
import zettai.domain.User
import zettai.domain.events.ToDoListEvent
import zettai.fp.Outcome
import zettai.fp.ZettaiError

/** 出来事が属する対象。user と listName から作る。 */
@JvmInline
value class EntityId(val value: String) {
    companion object {
        fun of(user: User, listName: ListName) = EntityId("${user.name}/${listName.name}")
    }
}

/** 出来事を保存する。 */
typealias EventAppender = (List<ToDoListEvent>) -> Outcome<ZettaiError, Unit>

/** 出来事を読み出す。追記順に並んで返る。 */
typealias EventReader = () -> Outcome<ZettaiError, List<ToDoListEvent>>
