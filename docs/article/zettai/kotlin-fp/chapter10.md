---
type: Article
title: "第 10 章 コンテキストを読み込み、コマンドを処理する"
description: "Zettai Kotlin FP 版の第 10 章。並行する command が event を重複させる問題を、待ち合わせで再現してから直す。環境を型引数と関数の引数で通す transaction、stream ごとの版と一意制約による楽観ロック、複数の stream を 1 つの transaction で書く契約を、in-memory と PostgreSQL で同じテストに通す。"
tags: [article, zettai, kotlin, functional-programming, arrow, postgresql, transaction, optimistic-locking]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-10-31T11:37:33Z }
---

# 第 10 章 コンテキストを読み込み、コマンドを処理する

前章で、event を PostgreSQL に追記しました。ただし、前章の store は、操作ごとに connection を開いて閉じていました。command 1 つの処理は、読み出し、判断、保存という 3 つの別々の transaction になります。

この章では、その間に何が入り込めるかを、先に再現します。

## モナドでデータベースにアクセスする

### 操作ごとに接続が作られる

前章の最後に、並行実行の限界を書きました。「同じリストへの command が同時に走ると、2 つとも古い状態を読んで判断し、両方が追記される」というものです。推論でした。この章では、再現します。

再現には、2 つの command が、どちらも保存の前に読み出す順序を作る必要があります。時間や実行の順序には頼りません。待ち合わせを使います。

```kotlin
private fun <Tx> bothReadBeforeEitherWrites(store: EventStore<Tx>): EventStore<Tx> {
    val arrived = AtomicInteger(0)
    val gate = CompletableDeferred<Unit>()
    return store.copy(
        readStream = { tx, stream ->
            val events = store.readStream(tx, stream)
            if (arrived.incrementAndGet() >= 2) gate.complete(Unit)
            withTimeout(GATE_TIMEOUT_MS) { gate.await() }
            events
        },
    )
}
```

読み出しの口を包んで、2 つ目の command が読み終えるまで、どちらも先へ進ませません。待ち合わせには時間切れを付けてあります。片方が先へ進めなくなる変更が入ったとき、テストが止まったままにならず、失敗として報告されるためです。この状態で、同じ `CreateToDoList` を 2 つ並行して走らせます。この仕組みを入れる前の store（前章の形）に対して、同じ `CreateToDoList` の並行実行を試すと、2 つの command がどちらも成功しました。当時のテストは、片方だけが成功することを期待して書いたものです。

### 何をすればいいか

直し方は 3 つあります。

| やること | 目的 |
| :--- | :--- |
| command 1 つの処理を、1 つの transaction に入れる | 読み出し、判断、保存の間に、別の transaction が入らないようにする |
| 読んだ版を持ち、書くときに確かめる | 読んだ後に、誰かが追記していたら、書かずに断る |
| 複数の stream への書き込みを、まとめて確定させる | 次の章の名前変更が、どちらか片方だけ残らないようにする |

## ContextReader を使用したコマンドの処理

既存 Kotlin 版は、接続を持ち回る計算を `ContextReader` という型で自作し、その `flatMap` が同じ接続を 2 つの計算に渡す形にしました。

Kotlin FP 版は、新しい型を作りません。環境（transaction の接続）は、関数の引数として渡します。環境を引数に取る関数は、環境を持たない場所（Hub や domain）から呼べません。`Tx` が `java.sql.Connection` のように具体的な型のときは、それだけで、`ContextReader` が型で守ろうとしたことを守れます。ただし、`Tx` の値を持っている adapter の内側では、transaction の外へ持ち出して呼ぶことを、型では防げません。in-memory の `Tx` は `Unit` なので、この保護は働きません。

### 環境を型引数で通す

transaction の中で動く操作は、環境 `Tx` を最初の引数に取ります。

```kotlin
data class StreamEvents(val events: List<ToDoListEvent>, val version: Int)
```

```kotlin
typealias Transaction<Tx> =
    suspend (suspend (Tx) -> Either<ZettaiError, List<ToDoListEvent>>) -> Either<ZettaiError, List<ToDoListEvent>>

/** transaction の中で、stream を版つきで読む。 */
typealias StreamReader<Tx> = suspend (Tx, StreamId) -> Either<PersistenceFailure, StreamEvents>
```

```kotlin
typealias StreamAppender<Tx> =
    suspend (Tx, Map<StreamId, Int>, List<ToDoListEvent>) -> Either<StoreError, Unit>
```

`Transaction` は、「環境を受け取る block」を受け取る関数です。block が `Right` を返したときだけ commit し、`Left` や例外のときは rollback します。読み出しと追記は `Tx` を引数に取るので、`Transaction` の外からは呼べません。

store は、この 3 つと、transaction の外で使う読み出しを束ねた値です。

```kotlin
data class EventStore<Tx>(
    val readAggregate: AggregateEventReader,
    val readUser: UserEventReader,
    val transaction: Transaction<Tx>,
    val readStream: StreamReader<Tx>,
    val append: StreamAppender<Tx>,
)
```

### Hub は transaction で包む

command の処理は、1 つの `transaction` の中に入ります。

```kotlin
        execute = { command ->
            store.transaction { tx ->
                either {
                    val read = command.streams.associateWith { store.readStream(tx, it).bind() }
                    val state = read.values.flatMap { it.events }.replayFrom()
                    val events = handle(command, state).bind()
                    store.append(tx, read.mapValues { it.value.version }, events).bind()
                    events
                }
            }
        },
```

読み出し、判断、保存が、1 つの `either` の中に並びます。読んだ版は、そのまま保存に渡されます。前章の `bind()` の連鎖と形は同じで、違うのは外側に `transaction` があること、環境 `tx` が見えることだけです。

Hub を組み立てるときにだけ `Tx` が現れます。組み立てた `ToDoListHub` の型には、`Tx` が出ません。web 層の変更は、エラーの写像を 1 つ足しただけです。Cucumber の経路は、変更していません。

## トランザクションの境界を決める

### 境界は command 1 つの処理

境界は、command 1 つの処理に置きます。コマンドは業務上の 1 つの意図だからです。HTTP のリクエスト 1 つに広げる案もありましたが、1 つのリクエストで複数の command を処理する場面がまだ無く、広げると「どこまでが 1 つの単位か」がコードから読み取れなくなります。判断は [ADR-042](../../../adr/ADR-042-transaction-scope-and-environment.md) に記録しました。

PostgreSQL の store では、境界は次の関数の中に 1 か所だけあります。

```kotlin
private suspend fun inTransaction(
    connect: () -> Connection,
    block: suspend (Connection) -> Either<ZettaiError, List<ToDoListEvent>>,
): Either<ZettaiError, List<ToDoListEvent>> =
    withContext(Dispatchers.IO) {
        Either.catch {
            connect().use { connection ->
                connection.autoCommit = false
                try {
                    val result = block(connection)
                    if (result.isRight()) connection.commit() else connection.rollback()
                    result
                } catch (failure: Throwable) {
                    runCatching { connection.rollback() }.onFailure(failure::addSuppressed)
                    throw failure
                }
            }
        }.fold({ it.toFailure().left() }, { it })
    }
```

connection は関数の中で開き、`use` で閉じます。Arrow の `Resource` は使いません。connection の寿命が、この関数の中で終わるためです。複数の transaction にまたがって connection を貸し出す必要が出たら、`Resource` を使います。

業務上の拒否（`Left`）も、競合も、例外も、rollback します。commit するのは、`Right` のときだけです。

### 版を確かめる

transaction だけでは、前節の問題は直りません。PostgreSQL の既定の隔離水準（READ COMMITTED）では、2 つの transaction が、どちらも「リストが無い」と読んでから、両方が書けてしまいます。そこで、版を持ちます。

stream ごとに連番の `version` を持ち、`UNIQUE (entity_id, version)` を付けます。

```sql
CREATE TABLE IF NOT EXISTS todo_list_event (
    id          BIGSERIAL PRIMARY KEY,
    entity_id   TEXT        NOT NULL,
    version     INTEGER     NOT NULL,
    event_type  TEXT        NOT NULL,
    payload     JSONB       NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (entity_id, version)
);
```

読み出しは event と一緒に版を返し、保存は読んだ版を受け取って、その次から連番を付けます。同じ版に 2 つの書き込みが来ると、後の挿入が一意制約に違反します。

```kotlin
private fun appendTo(
    connection: Connection,
    expected: Map<StreamId, Int>,
    events: List<ToDoListEvent>,
): Either<StoreError, Unit> {
    val appended = mutableMapOf<StreamId, Int>()
    for (event in events) {
        val stream = event.streamId()
        val base = expected[stream] ?: return PersistenceFailure("期待する版が無い stream: $stream").left()
        val version = base + (appended.merge(stream, 1, Int::plus) ?: 1)
        try {
            connection.insert(event, version)
        } catch (failure: SQLException) {
            return if (failure.sqlState == UNIQUE_VIOLATION) {
                ConcurrentModification(listOf(stream)).left()
            } else {
                failure.toFailure().left()
            }
        }
    }
    return Unit.right()
}
```

一意制約の違反の SQLState は `23505` です。PostgreSQL に対して実測しました。この違反だけを `ConcurrentModification` にし、他の失敗は `PersistenceFailure` のままにします。

```kotlin
data class ConcurrentModification(
    val streams: List<StreamId>,
) : StoreError {
    override val message: String =
        "Concurrent modification: ${streams.joinToString { "${it.user.name}/${it.listName.name}" }}"
}
```

HTTP では 409 に写像します。`when` に `else` が無いので、この型を足して写像を書き忘れると、compile error になります。

```kotlin
        is ConcurrentModification -> Status.CONFLICT
```

自動の再試行はしません。競合したときに、もう一度やり直すか、利用者に伝えるかは、業務の判断だからです。楽観ロックは保守的で、別々の項目の追加のように、互いに干渉しない command も、同じ stream に並行すれば片方が拒否されます。判断は [ADR-043](../../../adr/ADR-043-optimistic-locking-by-version.md) に記録しました。

### 並行する command の結果

読み出しを交差させた並行する `CreateToDoList` は、PostgreSQL では、後から書く側が `ConcurrentModification` で拒否されます。

```kotlin
    "PostgreSQL: 読み出しが交差した並行する CreateToDoList は、後から書く側が ConcurrentModification で拒否される" {
        val store = freshStore("PostgreSQL")
        val hub = todoListHub(bothReadBeforeEitherWrites(store))

        val results = coroutineScope {
            listOf(
                async(Dispatchers.Default) { hub.execute(CreateToDoList(alice, book)) },
                async(Dispatchers.Default) { hub.execute(CreateToDoList(alice, book)) },
            ).awaitAll()
        }

        results.count { it.isRight() } shouldBe 1
        results.mapNotNull { it.leftOrNull() } shouldBe listOf(ConcurrentModification(listOf(bookStream)))
        store.eventsOf(bookStream).count { it is ListCreated } shouldBe 1
    }
```

一方、in-memory の store は、transaction を `Mutex` で直列にします。

```kotlin
        transaction = { block -> mutex.withLock { block(Unit) } },
```

直列なので、読み出しは交差しません。後の command は更新後の状態を見て、`ListAlreadyExists` で拒否されます。競合は起きませんが、event が重複しないこと、更新が失われないことは、どちらの store でも同じです。つまり、2 つの store は同じ結果にはなりません。同じ契約を満たすだけです。契約は、次のテストで固定しています。

### 複数の stream への書き込み

次の章の名前変更は、旧い名前の stream と新しい名前の stream の、2 つに書きます。2 つへの書き込みが、片方だけ残ってはいけません。store の契約として、1 つでも版が古ければ、どの stream にも何も残らないことを確かめます。in-memory と PostgreSQL に、同じテストを通します。

```kotlin
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
```

`book` への 2 件は、`shop` の版が古いために、一緒に取り消されます。どの stream に競合があったかは、`ConcurrentModification` が教えます。PostgreSQL では、検出した最初の stream だけを持ちます。

## 設計を 2 回間違えた

この章では、2 つの間違いを、実際に踏みました。

### 1 回目: 待ち合わせの位置が、変えた口に追いついていなかった

最初の再現テストは、`readAggregate`（transaction の外の読み出し）に待ち合わせを置いていました。そのころの Hub は、`readAggregate` で読んでいたからです。

transaction を入れると、Hub の読み出しは、transaction の中の `readStream` に変わりました。待ち合わせは `readAggregate` に残ったままで、command には効かなくなりました。このとき、PostgreSQL の並行テストは通りました。ただし、通った理由が、狙った交差による競合の検出だったのかは分かりません。待ち合わせが効かなくても、2 つの command は別々の接続で並行して走るので、偶然に衝突して通ることがあります。

in-memory の並行する `AddToDoItem` のテストが落ちたことで、気づきました。in-memory の transaction は `Mutex` で直列なので、読み出しが交差しなければ、2 つの command は順に走り、どちらも成功します。期待と結果が違ったことで、待ち合わせの位置が、変えた口に追いついていないと分かりました。

待ち合わせを `readStream` に移し、store ごとに期待を分けました。待ち合わせは、並行して動く PostgreSQL のテストにだけ使います。そのうえで、PostgreSQL の版の連番をでたらめな値に変える変異を入れ、並行のテストが落ちることを確かめました。変異で検出を確かめないと、通ったことの意味は分かりません。

### 2 回目: 複数の stream を、stream ごとにまとめて挿入した

追記では、event を `stream` ごとにまとめてから挿入していました。1 つの stream に属する event を連続して挿入すれば、版の連番を数えるのが簡単だからです。

ところが、複数の stream にまたがる event を追記すると、挿入の順序が入力と変わります。ユーザー単位で読み出すと、event は `id` の順に返ります。入力が `[listA の event, listB の event, listA の event]` なら、返る順序は `[listA, listA, listB]` になります。PostgreSQL の property test が、この順序の崩れで落ちました。

入力の順に挿入し、版の連番は、stream ごとに数えるように直しました。いま `appendTo` にある、`appended` という表が、その名残です。

## データベースに対する射影のクエリ

command の処理は transaction に入りましたが、query は入れません。

```kotlin
        listRows = { user -> either { projectFrom(user, store.readUser(user).bind()) } },
```

query は、1 回の SQL で必要なものを読み出せます。単一の文は、それだけで原子的です。読み出しが複数の文に分かれていて、その間の整合が要るときに、transaction が必要になります。`getList` と `listRows` は、どちらも 1 回の読み出しなので、transaction の外に置きました。

command 側と query 側が、同じ event store を読むことは変わりません。command が追記した event は、commit された時点から、query で見えます。

## イベントソーシングによるドメインのモデリング

ここまでの章を、イベントソーシングとして振り返ります。

| 道具 | 役割 | 章 |
| :--- | :--- | :--- |
| command | 利用者の意図。拒否できる | 6 |
| event | 起きた事実。履歴として保存する | 5 |
| 状態 | event を畳み込んで得る。command の判断に使う | 5 |
| 射影 | event を別の形に畳み込む。表示に使う | 8 |
| event store | event を追記する。順序と版を持つ | 9・10 |
| transaction | command 1 つの処理を、1 つの単位にする | 10 |

状態を上書きしないので、履歴が残ります。別の形が欲しくなれば、event から射影を作れます。その代償も、ここまでに現れました。

- 状態を読むたびに、stream の event を畳み込みます。
- 読み出しと書き込みの間に、競合の検出が要ります。
- 規則を、状態と射影の 2 か所に書きます。
- 追記だけなので、間違った event を消せません。取り消しは、新しい event を足して表します。

画面が少なく、履歴に価値がなければ、割に合いません。この連載は、その設計を説明するための題材として使っています。

## まとめ

- 並行する command が event を重複させる問題を、待ち合わせで読み出しを交差させて再現しました。
- 環境（transaction の接続）を、型引数と関数の引数で通しました。環境を持たない場所からは、読み出しも追記も呼べません。
- 境界は、command 1 つの処理に置き、`inTransaction` の 1 か所に集めました。`Right` のときだけ commit し、`Left` と例外は rollback します。
- stream ごとの版と `UNIQUE (entity_id, version)` で、競合を検出しました。一意制約の違反だけを `ConcurrentModification` にし、HTTP は 409 に写像します。
- 複数の stream への書き込みは、どれか 1 つでも版が古ければ、どの stream にも残りません。in-memory と PostgreSQL が、同じ契約テストを満たします。
- 実装中に踏んだ間違いは 2 つでした。待ち合わせの位置が、変えた口に追いついていなかったこと。複数の stream を、stream ごとにまとめて挿入して、順序を崩したことです。

次の章では、名前変更を作ります。名前変更は、旧い名前の stream と新しい名前の stream に書きます。この章の、複数の stream を 1 つの transaction で書く契約が、そのまま土台になります。あわせて、利用者が入力した名前の誤りを、まとめて知らせる方法を扱います。

## この章で書いたコード

- 型と Hub: `apps/kotlin-fp/zettai/zettai-step6-transaction-validation/src/main/kotlin/zettai/application/ToDoListHub.kt`
- PostgreSQL の store: `apps/kotlin-fp/zettai/zettai-step6-transaction-validation/src/main/kotlin/zettai/adapter/postgres/PostgresEventStore.kt`
- SQL: `apps/kotlin-fp/zettai/zettai-step6-transaction-validation/src/main/kotlin/zettai/adapter/postgres/EventTable.kt`
- in-memory の store: `apps/kotlin-fp/zettai/zettai-step6-transaction-validation/src/main/kotlin/zettai/adapter/inmemory/InMemoryAdapters.kt`
- 競合の型: `apps/kotlin-fp/zettai/zettai-step6-transaction-validation/src/main/kotlin/zettai/domain/errors/ZettaiError.kt`
- 並行のテスト: `apps/kotlin-fp/zettai/zettai-step6-transaction-validation/src/test/kotlin/zettai/application/ConcurrentCommandTest.kt`
- store の契約テスト: `apps/kotlin-fp/zettai/zettai-step6-transaction-validation/src/test/kotlin/zettai/application/StoreContractTest.kt`

この章から `zettai-step6-transaction-validation` module に移ります。結合テストを含むため、Docker daemon が必要です（第 9 章を参照）。`apps/kotlin-fp/zettai/` で、次のように実行します。

Unix / Nix shell:

```text
bash ./gradlew :zettai-step6-transaction-validation:test
```

Windows PowerShell:

```text
.\gradlew.bat :zettai-step6-transaction-validation:test
```

## 参照

- Uberto Barbini『From Objects to Functions』第 10 章
- Michał Płachta『Grokking Functional Programming』Kotlin 版。環境を引数に取る関数と、副作用の境界を参照しました
- [ADR-042 transaction の境界を command 1 つの処理に置き、環境を型引数で通す](../../../adr/ADR-042-transaction-scope-and-environment.md)
- [ADR-043 楽観ロックを stream ごとの版と一意制約で行い、複数の stream を 1 つの transaction で書く](../../../adr/ADR-043-optimistic-locking-by-version.md)
