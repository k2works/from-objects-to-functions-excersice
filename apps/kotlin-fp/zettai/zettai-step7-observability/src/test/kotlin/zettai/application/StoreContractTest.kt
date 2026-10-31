package zettai.application

import arrow.core.right
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import zettai.domain.ToDoItem
import zettai.domain.events.ItemAdded
import zettai.domain.events.ListCreated

/** in-memory と PostgreSQL の store が、同じ契約を満たすことを確かめる。 */
class StoreContractTest : StringSpec({
    val book = stream("alice", "book")
    val shop = stream("alice", "shop")

    fun created(id: zettai.domain.StreamId) = ListCreated(id.user, id.listName)
    fun added(id: zettai.domain.StreamId, description: String) = ItemAdded(id.user, id.listName, ToDoItem(description))

    storeKinds.forEach { kind ->
        "$kind: 期待する版で追記でき、版が進む" {
            val store = freshStore(kind)

            store.appendWithVersions(mapOf(book to 0), listOf(created(book))) shouldBe Unit.right()
            store.versionOf(book) shouldBe 1

            store.appendWithVersions(mapOf(book to 1), listOf(added(book, "a"), added(book, "b"))) shouldBe Unit.right()
            store.versionOf(book) shouldBe 3
            store.eventsOf(book) shouldBe listOf(created(book), added(book, "a"), added(book, "b"))
        }

        "$kind: 古い版での追記は ConcurrentModification で拒否され、何も増えない" {
            val store = freshStore(kind)
            store.appendWithVersions(mapOf(book to 0), listOf(created(book))) shouldBe Unit.right()

            val result = store.appendWithVersions(mapOf(book to 0), listOf(created(book)))

            result.conflictStreams() shouldBe listOf(book)
            store.versionOf(book) shouldBe 1
        }

        "$kind: stream の版は stream ごとに独立している" {
            val store = freshStore(kind)

            store.appendWithVersions(mapOf(book to 0), listOf(created(book), added(book, "a"))) shouldBe Unit.right()
            store.appendWithVersions(mapOf(shop to 0), listOf(created(shop))) shouldBe Unit.right()

            store.versionOf(book) shouldBe 2
            store.versionOf(shop) shouldBe 1
        }

        "$kind: 複数の stream への追記は、すべて確定する" {
            val store = freshStore(kind)

            val result = store.appendWithVersions(
                mapOf(book to 0, shop to 0),
                listOf(created(book), created(shop), added(shop, "a")),
            )

            result shouldBe Unit.right()
            store.versionOf(book) shouldBe 1
            store.versionOf(shop) shouldBe 2
        }

        "$kind: 複数の stream のうち 1 つでも版が古ければ、どの stream にも何も残らない" {
            val store = freshStore(kind)
            store.appendWithVersions(mapOf(shop to 0), listOf(created(shop))) shouldBe Unit.right()

            val result = store.appendWithVersions(
                mapOf(book to 0, shop to 0),
                listOf(created(book), added(book, "a"), added(shop, "stale")),
            )

            result.conflictStreams() shouldBe listOf(shop)
            store.versionOf(book) shouldBe 0
            store.versionOf(shop) shouldBe 1
        }

        "$kind: 期待する版が無い stream への追記は、PersistenceFailure として拒否される" {
            val store = freshStore(kind)

            val result = store.appendWithVersions(mapOf(book to 0), listOf(created(book), created(shop)))

            result.isStoreError() shouldBe true
            result.conflictStreams() shouldBe null
            store.versionOf(book) shouldBe 0
            store.versionOf(shop) shouldBe 0
        }
    }
})
