package zettai.application

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import zettai.adapter.inmemory.inMemoryEventStore
import zettai.adapter.postgres.SharedPostgres
import zettai.adapter.postgres.postgresEventStore
import zettai.domain.ListName
import zettai.domain.StreamId
import zettai.domain.User
import zettai.domain.errors.PersistenceFailure
import zettai.domain.errors.StoreError
import zettai.domain.errors.ZettaiError
import zettai.domain.events.ToDoListEvent
import zettai.domain.events.streamId

/**
 * 読み出しと保存のラムダだけで Hub を作る、テスト用の組み立て。
 * transaction は block をそのまま実行し、保存は版を無視する。
 * 版や transaction の振る舞いは、store の契約テストと結合テストで確かめる。
 */
fun todoListHub(
    readAggregate: AggregateEventReader,
    readUser: UserEventReader,
    persist: suspend (List<ToDoListEvent>) -> Either<PersistenceFailure, Unit>,
): ToDoListHub =
    todoListHub(
        EventStore<Unit>(
            readAggregate = readAggregate,
            readUser = readUser,
            transaction = { block -> block(Unit) },
            readStream = { _, id -> readAggregate(id.user, id.listName).map { StreamEvents(it, it.size) } },
            append = { _, _, events -> persist(events) },
        ),
    )

/** 契約テストを通す store の種類。 */
val storeKinds = listOf("in-memory", "PostgreSQL")

suspend fun freshStore(kind: String): EventStore<*> =
    if (kind == "PostgreSQL") {
        SharedPostgres.reset()
        postgresEventStore(SharedPostgres::connect)
    } else {
        inMemoryEventStore()
    }

/** 期待する版を指定して、1 つの transaction で追記する。 */
suspend fun <Tx> EventStore<Tx>.appendWithVersions(
    expected: Map<StreamId, Int>,
    events: List<ToDoListEvent>,
): Either<ZettaiError, Unit> =
    transaction { tx -> append(tx, expected, events).map { events } }.map { }

/** transaction の中で、読んだ版をそのまま使って追記する。競合の無い状況で、前提を用意するために使う。 */
suspend fun <Tx> EventStore<Tx>.appendAuto(events: List<ToDoListEvent>): Either<ZettaiError, Unit> =
    transaction { tx ->
        either {
            val versions = events.map { it.streamId() }.distinct()
                .associateWith { readStream(tx, it).bind().version }
            append(tx, versions, events).bind()
            events
        }
    }.map { }

/** stream に追記済みの event の列。件数がその stream の版に等しい。 */
suspend fun EventStore<*>.eventsOf(stream: StreamId): List<ToDoListEvent> =
    readAggregate(stream.user, stream.listName).getOrElse { error(it.message) }

suspend fun EventStore<*>.versionOf(stream: StreamId): Int = eventsOf(stream).size

fun stream(user: String, list: String) = StreamId(User(user), ListName(list))

fun Either<ZettaiError, *>.conflictStreams(): List<StreamId>? =
    leftOrNull()?.let { (it as? zettai.domain.errors.ConcurrentModification)?.streams }

fun Either<ZettaiError, *>.isStoreError(): Boolean = leftOrNull() is StoreError
