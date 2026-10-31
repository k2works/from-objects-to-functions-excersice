package zettai

import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.list
import zettai.domain.ListName
import zettai.domain.ToDoItem
import zettai.domain.ToDoStatus
import zettai.domain.User
import zettai.domain.errors.InvalidTransition
import zettai.domain.errors.ItemNotFound
import zettai.domain.errors.ListAlreadyExists
import zettai.domain.errors.ListNotFound
import zettai.domain.errors.ZettaiError
import zettai.domain.events.ItemAdded
import zettai.domain.events.ItemStatusChanged
import zettai.domain.events.ListCreated
import zettai.domain.events.ToDoListEvent

val genUsers = listOf(User("alice"), User("bob"))
val genListNames = listOf(ListName("book"), ListName("shop"))
private val genDescriptions = listOf("a", "b", "c")

/** 少ない名前の組から作るので、同じリストへの event が重なりやすい。未作成のリストへの event も混ざる。 */
val arbEvent: Arb<ToDoListEvent> = Arb.choice(
    Arb.bind(Arb.element(genUsers), Arb.element(genListNames)) { user, listName ->
        ListCreated(user, listName)
    },
    Arb.bind(
        Arb.element(genUsers),
        Arb.element(genListNames),
        Arb.element(genDescriptions),
        Arb.enum<ToDoStatus>(),
    ) { user, listName, description, status ->
        ItemAdded(user, listName, ToDoItem(description, status = status))
    },
    Arb.bind(
        Arb.element(genUsers),
        Arb.element(genListNames),
        Arb.element(genDescriptions),
        Arb.enum<ToDoStatus>(),
    ) { user, listName, description, status ->
        ItemStatusChanged(user, listName, description, status)
    },
)

val arbEvents: Arb<List<ToDoListEvent>> = Arb.list(arbEvent, 0..30)

/** domain が拒否理由として返す error をすべて含む。 */
val arbError: Arb<ZettaiError> = Arb.choice(
    Arb.bind(Arb.element(genUsers), Arb.element(genListNames)) { user, listName ->
        ListNotFound(user, listName)
    },
    Arb.bind(Arb.element(genUsers), Arb.element(genListNames)) { user, listName ->
        ListAlreadyExists(user, listName)
    },
    Arb.bind(
        Arb.element(genUsers),
        Arb.element(genListNames),
        Arb.element(genDescriptions),
    ) { user, listName, description ->
        ItemNotFound(user, listName, description)
    },
    Arb.bind(
        Arb.element(genDescriptions),
        Arb.enum<ToDoStatus>(),
        Arb.enum<ToDoStatus>(),
    ) { description, from, to ->
        InvalidTransition(description, from, to)
    },
)
