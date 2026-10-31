---
type: Article
title: "第 9 章 モナドによる安全なデータ永続化"
description: "Zettai Kotlin FP 版の第 9 章。イベントを PostgreSQL に追記する。JDBC の副作用と失敗を suspend と Either の境界に閉じ込め、flatMap の三法則を property test で確かめる。Testcontainers の結合テスト、jsonb の正規化、区切り文字による集約の衝突、3 経路目の受け入れテストを TDD で示す。"
tags: [article, zettai, kotlin, functional-programming, arrow, postgresql, testcontainers, monad]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-10-31T10:33:19Z }
---

# 第 9 章 モナドによる安全なデータ永続化

ここまで、データはメモリの中にありました。アプリケーションを再起動すると消えます。

この章でイベントを保存します。そして、この連載で 3 つめの抽象概念が現れます。ただし、この章で作るのは adapter と結合テストまでです。実際に起動するアプリケーションへの配線は扱いません。

## 安全に永続化する

何を保存するかを決めます。ここが設計の分かれ道です。

### 状態を保存しない

普通の設計なら、`todo_list` と `todo_item` のテーブルを作り、項目の状態が変わったら UPDATE します。**この設計は採りません。** 第 5 章で決めたことと矛盾するからです。状態を上書きするのではなく、起きたことを並べて残します。メモリの中では出来事を残しているのに、データベースでは上書きすると、「なぜ今この状態なのか」がデータベースを見ても分かりません。

### 出来事を保存する

保存するのは出来事だけです。テーブルは 1 つになります。

```sql
CREATE TABLE IF NOT EXISTS todo_list_event (
    id          BIGSERIAL PRIMARY KEY,
    entity_id   TEXT        NOT NULL,
    event_type  TEXT        NOT NULL,
    payload     JSONB       NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

スキーマは既存 Kotlin 版と同じです。現在の状態は、出来事を読み出して畳み込んで得ます。第 5 章の `replayFrom` と第 8 章の `projectFrom` が、そのまま使えます。

判断したことを書き残します。`id` を順序の根拠にし、`recorded_at` は使いません。同じ時刻に複数の出来事が記録されうるからです。追記だけで UPDATE も DELETE もしないので、正規化が解く更新時の不整合は起きません。

### この設計の限界

正直に書きます。状態を読むたびに、その集約の出来事を全部畳み込みます。出来事が増えると遅くなります。実務ではスナップショットや、保存した射影（read model）を使いますが、この連載では扱いません。

ただし第 8 章で、読む単位を集約に絞りました。全ユーザー・全リストの出来事を毎回読む形は、ここで避けられています。この絞り込みが、データベースの `entity_id` で絞る形にそのまま対応します。

もう 1 つ、この章の実装には限界があります。読み出し、判断、保存は別々の接続で行われ、版の確認がありません。同じリストへの command が同時に走ると、2 つとも古い状態を読んで判断し、両方が追記されます。たとえば `CreateToDoList` が同時に 2 件走ると、`ListCreated` が 2 件記録されます。間に `ItemAdded` が入ると、event の順は `ListCreated`、`ItemAdded`、`ListCreated` になり、第 5 章の規則（重複した `ListCreated` はリストを空へ戻す）によって、replay した状態から項目が消えます。`ChangeItemStatus` が同時に走ると、遷移表で許されない経路を通った状態にもなりえます。これは実装を読んだ上での推論で、この章のテストでは再現していません。command 1 つの処理を 1 つの transaction にして競合を検出する仕組みは、次の章で扱います。

### 既存 Kotlin 版との違い

| | 既存 Kotlin 版 | Kotlin FP 版 |
| :--- | :--- | :--- |
| スキーマ | `todo_list_event` | 同じ |
| DB の用意 | `docker-compose.yml` と CI のサービスコンテナ | Testcontainers が起動する container |
| `payload` の組み立て | 手書きの JSON 文字列 | `jsonb_build_object` と bind parameter |
| 失敗の表し方 | `Outcome` と `PersistenceError` | `Either<PersistenceFailure, A>` |
| 合成の抽象 | 自作の `ContextReader` | `Either` の `flatMap` と Raise の `bind` |

## PostgreSQL と結合テスト

データベースを用意します。読者が手元で同じものを動かせることを優先します。

既存 Kotlin 版は `docker-compose.yml` に service を足し、Testcontainers を使いませんでした。Kotlin FP 版は [Testcontainers](https://java.testcontainers.org/) を使います。既存 Kotlin 版が退けた 3 つの理由に、次のように答えました。判断は [ADR-041](../../../adr/ADR-041-testcontainers-for-integration-tests.md) に記録しています。

| 既存 Kotlin 版が採らなかった理由 | Kotlin FP 版の答え |
| :--- | :--- |
| 依存を増やさない | この版の主題は、ライブラリに委ねたとき設計がどう変わるかを見ることなので、依存を増やすこと自体は避けません。ただし test の範囲に限ります |
| 読者に実体を見せたい | 失います。`docker compose up -d` で DB が動いている様子を見る機会はなくなります。代わりに、container の接続情報がテストへ渡る様子がコードに現れます |
| リポジトリの作法 | 既存の `docker-compose.yml` に service を足さず、変更しません。CI にも固定の service を置きません |

テストが自分で container を起動し、container が公開する JDBC URL、ユーザー、パスワードを受け取ります。ポートも認証情報も、コードに書きません。

```kotlin
object SharedPostgres {
    private val container: PostgreSQLContainer by lazy {
        PostgreSQLContainer("postgres:18-alpine").also { it.start() }
    }

    fun connect(): Connection =
        DriverManager.getConnection(container.jdbcUrl, container.username, container.password)
```

container は JVM で 1 つだけ起動し、全テストで共有します。起動に時間がかかるためです。そのかわり、テスト同士が状態を共有しないよう、テストごとにテーブルを作り直します。

```kotlin
    suspend fun reset() {
        connect().use { connection ->
            connection.createStatement().use { it.execute("DROP TABLE IF EXISTS todo_list_event") }
        }
        createSchema(::connect).getOrElse { error("スキーマの作成に失敗: ${it.reason}") }
    }
```

結合テストは、Docker が使えず container を起動できないとき、skip ではなく失敗するように作りました。Docker が使えない環境では、Testcontainers が `Could not find a valid Docker environment. Please see logs and check configuration` という例外で container の起動に失敗します。この文言は、Docker のソケットを渡さない container の中で、テストと同じ起動処理（`PostgreSQLContainer` の `start`）を動かして確かめました。Gradle のテスト結果での見え方までは、確かめていません。skip にすると、「DB が無いから通った」のか「実装が正しいから通った」のかが区別できません。結合テストは `./gradlew check` に含めます。別のジョブに分けると、ローカルの `check` と CI の判定が食い違うからです。

### 手元で動かすために

Docker daemon が必要です。`docker version` で Client と Server の両方が表示されれば、使える状態です。Windows と macOS では、Docker Desktop を起動しておいてください。初回は `postgres:18-alpine` の image を取得するので、ネットワークと時間が必要です。2 回目以降は、取得済みの image を使います。

Docker を使えない場合は、PostgreSQL に触れない範囲のテストを絞って実行できます。第 8 章までの範囲は、Docker なしで確かめられます。`apps/kotlin-fp/zettai/` で、次のように実行します。

Unix / Nix shell:

```text
bash ./gradlew :zettai-step5-cqrs-persistence:test --tests "zettai.domain.*" --tests "zettai.application.ToDoListHubTest" --tests "zettai.web.*" --tests "zettai.adapter.inmemory.*" --tests "zettai.smoke.*"
```

Windows PowerShell:

```text
.\gradlew.bat :zettai-step5-cqrs-persistence:test --tests "zettai.domain.*" --tests "zettai.application.ToDoListHubTest" --tests "zettai.web.*" --tests "zettai.adapter.inmemory.*" --tests "zettai.smoke.*"
```

## Kotlin でデータベースに接続する

ORM は使いません。この章の操作は、追記と読み出しの 2 つだけです。JDBC を直接使います。判断は [ADR-040](../../../adr/ADR-040-jdbc-blocking-boundary.md) に記録しました。

### 素朴に書くと何が困るか

素朴に書くと、次の 3 つに困ります。

| 素朴な書き方 | 困ること |
| :--- | :--- |
| JDBC を `suspend` の中でそのまま呼ぶ | blocking の呼び出しが隠れ、呼び出し側のスレッドをふさぐ |
| JDBC の例外をそのまま投げる | 呼び出し側の型に現れず、domain や HTTP まで例外が漏れる |
| JSON を文字列で組み立てる | 引用符や改行の escape を自分で書く。しかも `jsonb` は文字列を保たない |

最初の 2 つは、小さな関数 1 つにまとめます。

```kotlin
private suspend fun <A> blocking(block: () -> A): Either<PersistenceFailure, A> =
    withContext(Dispatchers.IO) { Either.catch(block) }
        .mapLeft { PersistenceFailure("${it::class.simpleName}: ${it.message}") }
```

blocking の呼び出しを `Dispatchers.IO` で実行し、例外を `Left` にします。`Either.catch` は `CancellationException` を捕まえず投げ直すので、coroutine のキャンセルを握りつぶしません。`try` と `catch (e: Exception)` で書くと、これを自分で防ぐ必要があります。なお、実行中の JDBC の呼び出しは、coroutine をキャンセルしても止まりません。キャンセルは、呼び出しが終わって coroutine に戻った時点で効きます。

ただし、HTTP の thread は、まだふさがれます。web の `runBlocking` が、同期の http4k handler で結果を待つためです。`Dispatchers.IO` に出す利点は、blocking を adapter に隠すことと、非同期の server に替えたときに port をそのまま使えることです。

## データベースを準備する

接続の作り方は、外から関数で受け取ります。

```kotlin
fun postgresEventStore(connect: () -> Connection): EventStore =
    EventStore(
        readAggregate = { user, listName ->
            query(connect, EventTable.SELECT_BY_ENTITY, entityId(user, listName))
        },
        readUser = { user ->
            query(connect, EventTable.SELECT_BY_USER, userPrefix(user))
        },
        persist = { events -> append(connect, events) },
    )
```

`connect` は `() -> Connection` という関数です。テストは Testcontainers の接続を渡します。アプリケーションの起動時の配線（`main` と接続設定）は、この章では扱いません。同じ形の関数で、別の接続を渡せます。connection は操作ごとに開き、`use` で閉じます。Arrow の `Resource` でも同じ後始末ができることを、使い捨てのテストで確かめました。成功しても例外でも、どちらも connection は閉じました。この章の操作は 1 回の呼び出しで完結するので、より単純な `use` を選びました。1 つの connection を複数の操作にまたがって貸す必要が出たら、`Resource` を使います。

追記は 1 回の呼び出しで、まとめて確定させます。

```kotlin
private suspend fun append(
    connect: () -> Connection,
    events: List<ToDoListEvent>,
): Either<PersistenceFailure, Unit> =
    blocking {
        connect().use { connection ->
            connection.autoCommit = false
            try {
                events.forEach { event -> connection.insert(event) }
                connection.commit()
            } catch (failure: Throwable) {
                // close に任せず、確定していない書き込みを明示的に取り消す。pool の接続でも前提が崩れない。
                runCatching { connection.rollback() }.onFailure(failure::addSuppressed)
                throw failure
            }
        }
    }
```

途中で失敗すれば、明示的に rollback して例外を投げ直し、`blocking` が `PersistenceFailure` にします。close に任せず rollback するのは、pool の接続に替えたときも前提が崩れないためです。2 件目の INSERT が失敗する batch を作り、1 件目も残らないことをテストで確かめました。NUL 文字は PostgreSQL の text に入れられないので、これで 2 件目の INSERT が失敗します。ただし、このテストは rollback の呼び出しを消しても通ります。PostgreSQL の driver は、commit していない接続を閉じると書き込みを破棄するためです。明示的な rollback は pool の接続に替えたときの備えで、この章のテストでは確かめていません。

```kotlin
    "途中で失敗した batch は 1 件も残らない" {
        val store = postgresEventStore(SharedPostgres::connect)
        // NUL 文字は PostgreSQL の text に入らないので、2 件目の INSERT が失敗する
        val result = store.persist(
            listOf(ListCreated(alice, book), ItemAdded(alice, book, ToDoItem("a\u0000b"))),
        )

        result.leftOrNull().shouldBeInstanceOf<PersistenceFailure>()
        store.events(alice, book) shouldBe emptyList()
    }
```

command の読み出しから保存までをまたぐ transaction は、ここでは扱いません。

event の種類ごとに、`payload` を SQL で組み立てます。

```kotlin
    const val INSERT_LIST_CREATED = """
        INSERT INTO todo_list_event (entity_id, event_type, payload)
        VALUES (?, 'ListCreated', jsonb_build_object('user', ?::text, 'listName', ?::text))
    """
```

JSON の文字列を Kotlin で組み立てません。値は bind parameter で渡します。引用符、バックスラッシュ、改行を含む文字列と null が、escape を書かずに往復することを確かめました。

## 関数型スタイルでリモートデータにアクセスする

データベースの読み書きは、失敗するかもしれない操作です。port の戻り値に、その失敗を出します。

```kotlin
typealias AggregateEventReader =
    suspend (User, ListName) -> Either<PersistenceFailure, List<ToDoListEvent>>
```

```kotlin
typealias UserEventReader = suspend (User) -> Either<PersistenceFailure, List<ToDoListEvent>>

typealias EventPersister = suspend (List<ToDoListEvent>) -> Either<PersistenceFailure, Unit>
```

失敗の型は `ZettaiError` ではなく `PersistenceFailure` です。読み出しと保存で起こり得る失敗は、これだけだからです。`Either<A, B>` は `A` と `B` について共変なので、`Either<PersistenceFailure, X>` は `Either<ZettaiError, X>` として扱えます。型の上で、何が起こり得て何が起こり得ないかが読めます。

```kotlin
data class PersistenceFailure(
    val reason: String,
) : ZettaiError {
    override val message: String = "Persistence failure"
}
```

原因は `reason` に持ち、`message` には含めません。`message` は HTTP の response の本文になるので、接続先などの内部情報を漏らさないためです。

### map で繋ぐ

読み出した結果に、続けて処理をします。成功なら次へ、失敗ならそのまま流します。

```text
Either<PersistenceFailure, List<ToDoListEvent>>.map { it.replayFrom() }
    -> Either<PersistenceFailure, ToDoListState>
```

### map では足りない

`map` に、`Either` を返す関数を渡すと、`Either` が二重になります。

<!-- code-check: ignore map では足りない理由を示す型の例 -->

```text
Either<E, Int>.map { Either<E, String> }  ->  Either<E, Either<E, String>>
```

読み出して、判断して、保存する。この 3 つは、それぞれ失敗しうる操作です。毎回、内側の `Either` を開ける必要が出ます。

### flatMap で繋ぐ

内側を開けて繋ぐ操作が `flatMap` です。第 7 章で触れた Raise の `bind()` は、この `flatMap` の連鎖と同じ結果になります。上から順に読める形で書けます。次の節で、同じ結果になることをテストで確かめます。

```kotlin
        execute = { command ->
            either {
                val state = readAggregate(command.user, command.listName).bind().replayFrom()
                val events = handle(command, state).bind()
                persist(events).bind()
                events
            }
        },
```

読み出しの失敗、command の拒否、保存の失敗のどれが起きても、そこで打ち切って同じ `Left` を返します。検証に成功した event だけが保存され、読み出しや保存が失敗したとき成功は返りません。

query 側も同じ形です。第 8 章で `List` を返していた `listRows` は `Either` を返すようになり、第 8 章から `Either` を返していた `getList` は、読み出しの失敗も受け取るようになりました。どちらも、読み出しの失敗を `bind()` で受け取ります。

```kotlin
        getList = { user, listName ->
            either {
                readAggregate(user, listName).bind()
                    .replayFrom()
                    .listFor(user, listName)
                    ?: raise(ListNotFound(user, listName))
            }
        },
        listRows = { user -> either { projectFrom(user, readUser(user).bind()) } },
```

リストが無いことは `raise` で `ListNotFound` にします。読み出しの失敗は `ListNotFound` にせず、そのまま返します。第 8 章で先に確かめた、`Either` と `List` を重ねた `map` の法則が、ここで現実の型になりました。

### 何もしない計算

`flatMap` と対になるのが、値を包むだけの操作 `right()` です。何もせず、値をそのまま成功として返します。次の節の左恒等則と右恒等則は、この操作に関する法則です。

## モナドの力を探る

`flatMap` が予測可能に繋がるには、3 つの性質が必要です。`pure(a)` は値 `a` を成功として包む操作で、`f` と `g` は `Either` を返す関数です。テストでは、`value.right()` を `pure` という名前の関数にして、法則の式に合わせています。

1. 左恒等則: `pure(a).flatMap(f)` は `f(a)` と同じ
2. 右恒等則: `m.flatMap(pure)` は `m` と同じ
3. 結合律: `m.flatMap(f).flatMap(g)` は `m.flatMap { x -> f(x).flatMap(g) }` と同じ

第 5 章のモノイド、第 7 章のファンクタと同じように、法則を property test で確かめます。成否が分かれる関数を、失敗の種類まで含めて生成します。

```kotlin
    // 割り切れるかどうかで成否が分かれる関数。成功も失敗も、失敗の種類も生成される。
    val steps: Arb<(Int) -> Either<ZettaiError, Int>> =
        Arb.bind(Arb.int(2..5), Arb.int(-5..5), arbError) { divisor, offset, error ->
            { x: Int -> if (x % divisor == 0) error.left() else (x + offset).right() }
        }
```

```kotlin
    "左恒等則：pure(a).flatMap(f) は f(a) と同じ" {
        checkAll(values, steps) { a, f ->
            pure(a).flatMap(f) shouldBe f(a)
        }
    }
```

```kotlin
    "結合律：flatMap を続けても、中にまとめても同じ" {
        checkAll(results, steps, steps) { m, f, g ->
            m.flatMap(f).flatMap(g) shouldBe m.flatMap { x -> f(x).flatMap(g) }
        }
    }
```

Hub で使っている `bind()` の連鎖が `flatMap` と同じ結果になることも、同じ方法で確かめます。

```kotlin
    // Hub の `either { ... bind() ... }` は、flatMap の連鎖を上から読める形で書いたもの。
    "Raise の bind の連鎖は flatMap と同じ結果になる" {
        checkAll(results, steps, steps) { m, f, g ->
            val withBind: Either<ZettaiError, Int> = either { g(f(m.bind()).bind()).bind() }

            withBind shouldBe m.flatMap(f).flatMap(g)
        }
    }
```

この 3 つの性質を満たす `flatMap` と `pure` を持つ構造を、モナドと呼びます。名前は最後に出しました。困りごとから出発して構造を作り、あとで名前を知る順序は、モノイド、ファンクタ、CQRS のときと同じです。

既存 Kotlin 版は、接続を持ち回る計算を `ContextReader` として自作し、その `flatMap` の法則を確かめます。Kotlin FP 版では、この章で自作しません。確かめているのは Arrow の `Either` の法則で、自分のコードの正しさではありません。それでも、Hub が頼っている性質を明文化し、`bind` の連鎖との等価性を固定する価値があります。`ContextReader` に相当する、環境を持ち回る計算は、第 10 章で扱います。

## 読み戻す

保存した event を読み戻して、元と等しいことを確かめます。テストを先に書きました。

```kotlin
    "追記した event を同じ順序で読み戻す" {
        val store = postgresEventStore(SharedPostgres::connect)
        val events = listOf(
            ListCreated(alice, book),
            ItemAdded(alice, book, ToDoItem("write chapter")),
            ItemStatusChanged(alice, book, "write chapter", ToDoStatus.InProgress),
        )

        store.append(events)

        store.events(alice, book) shouldBe events
    }
```

`append` と `events` は、`Either` の結果を取り出すテストの補助関数です。

### JSONB で踏んだこと

`jsonb` は、入れた文字列を保ちません。実際に試しました。

```text
入れた値   {"b":1,   "a":[2, 3]}
読み出し値 {"a": [2, 3], "b": 1}
```

空白は決まった形に整えられ、キーは並べ替えられます。書いた JSON の文字列と、読み戻した文字列は、一致するとは限りません。既存 Kotlin 版の第 9 章にも、同じ「JSONB で踏んだこと」の節があります。Kotlin FP 版は JSON の文字列を Kotlin で組み立てないので、この違いに悩まされません。それでも、文字列で比べるテストは書かないと決めました。読み戻した event が元の event と等しいことで確かめます。読み出しも、`payload->>'user'` のように、項目ごとに取り出します。

### 区切り文字で別の集約が衝突する

`entity_id` を `{user}/{listName}` の文字列にすると、別の集約が衝突することに気づきました。`User("a/b")` と `ListName("c")` の組と、`User("a")` と `ListName("b/c")` の組が、どちらも `a/b/c` になります。HTTP の path から `/` を含む名前は来ませんが、domain の `User` と `ListName` は文字列なら何でも受け取ります。

そこで、各部分の `%` と `/` を置き換えてから連結します。

```kotlin
private fun String.escapeEntityPart(): String = replace("%", "%25").replace("/", "%2F")

internal fun entityId(user: User, listName: ListName): String =
    "${userPrefix(user)}${listName.name.escapeEntityPart()}"

/** ユーザー単位で読むときの前方一致の接頭辞。 */
internal fun userPrefix(user: User): String = "${user.name.escapeEntityPart()}/"
```

通常の名前では既存 Kotlin 版と同じ値になり、`/` と `%` を含む名前でだけ変わります。ユーザー単位で読むとき `LIKE` を使わないのは、符号化した値の `%` がワイルドカードになるからです。

このテストが本当に衝突を検出するか、実装を壊して確かめました。置き換えを外すと、このテストだけが失敗しました。

### 壊れた行を黙って捨てない

読み出した行が壊れていたらどうなるか。未知の `event_type`、必須の項目が無い payload、不正な状態、不正な期日を、行として直接挿入して試しました。どれも `PersistenceFailure` の `Left` になります。正しい行が同じ集約に残っていても、壊れた行を無視して一部だけを返すことはしません。

第 5 章の `apply` は、存在しないリストへの event を無視して状態を変えません。永続化を入れると、壊れた event 列が入る危険が現れます。無視する規則を domain に残したまま、データベースから読む境界では、壊れた行を失敗として返すことにしました。

HTTP では、`PersistenceFailure` を 500 に写像します。本文は固定の `Persistence failure` で、原因は含めません。

## 3 経路目

第 3 章の Cucumber の scenario を、3 つめの経路で動かします。domain、HTTP、そして PostgreSQL です。

```kotlin
fun postgresActions(): ZettaiActions {
    runBlocking { SharedPostgres.reset() }
    fun newHub(): ToDoListHub = todoListHub(postgresEventStore(SharedPostgres::connect))
    val writer = newHub()
    return ZettaiActions(
        name = "PostgreSQL",
        setUp = writer::setUpWithCommands,
        getToDoList = { user, listName -> newHub().getList(user, listName).itemsOrNull() },
    )
}
```

前提を書き込む Hub と、読み出す Hub を別々に組み立てます。読み出しのたびに Hub と store を組み直すので、状態がプロセス内のオブジェクトではなくデータベースにあることを確かめる経路になります。JVM を実際に再起動するわけではありません。in-memory の経路では、組み直すとリストは消えます。

もう 1 つ工夫があります。読み出しの失敗を「見つからない」に紛れさせません。

```kotlin
private fun Either<ZettaiError, ToDoList>.itemsOrNull(): List<ToDoItem>? =
    fold(
        ifLeft = { failure -> if (failure is ListNotFound) null else error("取得に失敗: ${failure.message}") },
        ifRight = { it.items },
    )
```

DB の接続が切れていたのに「リストは見つからない」の scenario が通ってしまうと、緑のまま何も確かめていないことになります。HTTP の経路も同じです。404 だけを「見つからない」とみなし、500 は失敗にします。PostgreSQL 経路が本当に評価されているか、読み出し前にテーブルを空にして確かめました。`PostgreSQL 経路` の失敗として scenario が落ちました。

## まとめ

- 状態を上書きせず、出来事だけを 1 つのテーブルに追記しました。
- 結合テストの DB は Testcontainers に委ねました。接続情報は container が公開する値だけを使い、`check` に含めました。
- JDBC を直接使い、blocking は `Dispatchers.IO` に閉じ込め、例外は `Either.catch` で `Left` にしました。キャンセルは握りつぶしません。
- 読み出しと保存の失敗を、`Either<PersistenceFailure, A>` で port の型に出しました。途中で失敗した batch は、明示的に rollback し、1 件も残しません。
- `flatMap` の三法則と、`bind` の連鎖が `flatMap` と同じ結果になることを、property test で確かめました。
- `jsonb` の正規化と、区切り文字による集約の衝突を、テストで見つけて直しました。
- 同じ scenario を、domain、HTTP、PostgreSQL の 3 経路で動かしました。

次の章では、読み出しから保存までを 1 つの文脈で包みます。ここでは、操作ごとに connection を開いて閉じました。command 1 つの処理を、1 つの transaction にまとめる必要があります。

## この章で書いたコード

- PostgreSQL の event store: `apps/kotlin-fp/zettai/zettai-step5-cqrs-persistence/src/main/kotlin/zettai/adapter/postgres/PostgresEventStore.kt`
- SQL と `entity_id`: `apps/kotlin-fp/zettai/zettai-step5-cqrs-persistence/src/main/kotlin/zettai/adapter/postgres/EventTable.kt`
- 永続化の失敗: `apps/kotlin-fp/zettai/zettai-step5-cqrs-persistence/src/main/kotlin/zettai/domain/errors/ZettaiError.kt`
- Hub: `apps/kotlin-fp/zettai/zettai-step5-cqrs-persistence/src/main/kotlin/zettai/application/ToDoListHub.kt`
- 結合テスト: `apps/kotlin-fp/zettai/zettai-step5-cqrs-persistence/src/test/kotlin/zettai/adapter/postgres/PostgresEventStoreTest.kt`
- モナド則のテスト: `apps/kotlin-fp/zettai/zettai-step5-cqrs-persistence/src/test/kotlin/zettai/domain/errors/EitherMonadLawTest.kt`
- 3 経路目: `apps/kotlin-fp/zettai/zettai-step5-cqrs-persistence/src/test/kotlin/zettai/acceptance/ScenarioActions.kt`

結合テストを含むこの章の module のテストは、Docker daemon が動いている環境で、`apps/kotlin-fp/zettai/` から実行できます。

Unix / Nix shell:

```text
bash ./gradlew :zettai-step5-cqrs-persistence:test
```

Windows PowerShell:

```text
.\gradlew.bat :zettai-step5-cqrs-persistence:test
```

## 参照

- Uberto Barbini『From Objects to Functions』第 9 章
- Michał Płachta『Grokking Functional Programming』Kotlin 版。副作用を境界へ押し出し、`flatMap` で失敗しうる計算を繋ぐ説明を参照しました
- [ADR-040 JDBC を直接使い、blocking は Dispatchers.IO に閉じ込め、connection は use で閉じる](../../../adr/ADR-040-jdbc-blocking-boundary.md)
- [Testcontainers for Java](https://java.testcontainers.org/)
- [Arrow Either](https://arrow-kt.io/learn/typed-errors/either-and-ior/)
