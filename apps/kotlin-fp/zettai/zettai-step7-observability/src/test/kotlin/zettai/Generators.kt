package zettai

import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import zettai.domain.ListName
import zettai.domain.StreamId
import zettai.domain.ToDoItem
import zettai.domain.ToDoStatus
import zettai.domain.User
import zettai.domain.commands.AddToDoItem
import zettai.domain.commands.ChangeItemStatus
import zettai.domain.commands.CreateToDoList
import zettai.domain.commands.ToDoListCommand
import zettai.domain.errors.ConcurrentModification
import zettai.domain.errors.InvalidTransition
import zettai.domain.errors.ItemNotFound
import zettai.domain.errors.ListAlreadyExists
import zettai.domain.errors.ListNotFound
import zettai.domain.errors.PersistenceFailure
import zettai.domain.errors.ZettaiError
import zettai.domain.events.ItemAdded
import zettai.domain.events.ItemStatusChanged
import zettai.domain.events.ListCreated
import zettai.domain.events.ListRenamed
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
    Arb.bind(Arb.element(genUsers), Arb.element(genListNames), Arb.element(genListNames)) { user, from, to ->
        ListRenamed(user, from, to)
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
    Arb.element("connection refused", "disk full").map { PersistenceFailure(it) },
    Arb.bind(Arb.element(genUsers), Arb.element(genListNames)) { user, listName ->
        ConcurrentModification(listOf(StreamId(user, listName)))
    },
)

val arbCommand: Arb<ToDoListCommand> = Arb.choice(
    Arb.bind(Arb.element(genUsers), Arb.element(genListNames)) { user, listName ->
        CreateToDoList(user, listName)
    },
    Arb.bind(Arb.element(genUsers), Arb.element(genListNames), Arb.element(genDescriptions)) { user, listName, d ->
        AddToDoItem(user, listName, ToDoItem(d))
    },
    Arb.bind(
        Arb.element(genUsers),
        Arb.element(genListNames),
        Arb.element(genDescriptions),
        Arb.enum<ToDoStatus>(),
    ) { user, listName, description, status ->
        ChangeItemStatus(user, listName, description, status)
    },
)

/** 2 つの列を交互に混ぜる。末尾の連結だけでは見つからない、途中に挟まる event を作る。 */
fun <T> interleave(first: List<T>, second: List<T>): List<T> =
    (0 until maxOf(first.size, second.size)).flatMap { index ->
        listOfNotNull(first.getOrNull(index), second.getOrNull(index))
    }
