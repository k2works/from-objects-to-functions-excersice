---
type: Article
title: "第 12 章 監視と関数型JSON"
description: "Zettai Kotlin FP 版の第 12 章。Hub と event store を包む decorator と HTTP の filter で、機密を含まない構造化ログを出す。載せるものを LogRecord の型で決め、辞書と JSON の双方向変換を純粋な Converter で表し、Encoder と Decoder がプロファンクタの形をしていることを法則のテストで確かめる。"
tags: [article, zettai, kotlin, functional-programming, arrow, logging, json, profunctor]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-10-31T13:19:51Z }
---

# 第 12 章 監視と関数型JSON

前章までで、command の処理は 1 つの transaction に入り、入力の誤りはまとめて返るようになりました。動かし続けるには、もう 1 つ要ります。何が起きたかを、後から追えることです。

この章では、ログを足します。ただし、domain にも application にも、ログのための行は 1 行も足しません。

## アプリケーションを監視する

### 知りたいことと、載せてはならないこと

運用で知りたいのは、次のことです。

- どの操作が、どう終わったか（成功したか、どの理由で拒否されたか）
- どれくらい時間がかかったか
- どの利用者の、どのリストで起きたか

載せてはならないものもあります。

| 載せない | 理由 |
| :--- | :--- |
| 項目の説明、期日 | 利用者が書いた内容そのもの |
| event の payload | 同上 |
| `PersistenceFailure.reason` | 接続先などの内部情報を含みうる |
| 例外のスタックトレース | 同上 |
| request の query と body | 入力そのもの |

ログを足す方法は、大きく 2 つあります。操作の中に `log(...)` を書き足すか、操作を包むかです。前者は、domain の純粋な関数に副作用を入れます。この連載では、既存の境界テストが禁じています。後者を選びます。

### 包む対象

`ToDoListHub` は、`getList`、`listRows`、`execute` の 3 つの関数を持つ data class です。`EventStore<Tx>` も、読み出しと追記の関数を持つ data class です。どちらも関数の束なので、関数を 1 つずつ包んだ新しい束を返せます。

包む場所は、次のとおりです。

| 場所 | 包むもの |
| :--- | :--- |
| `zettai.adapter.logging` | Hub の 3 つの操作。store の `readAggregate`、`readUser`、`readStream`、`append` |
| `zettai.web` | HTTP の request（filter） |
| `zettai.logging` | ログの 1 行の型 `LogRecord` |

`transaction` は包みません。境界の意味を変えないためです。決定は [ADR-045](../../../adr/ADR-045-logging-as-decorator-with-typed-record.md) に記録しました。

## 構造化されたロギング

### 載せるものを、型で決める

ログの 1 行は、次の型です。

```kotlin
data class LogRecord(
    val at: Instant,
    val kind: String,
    val name: String,
    val outcome: String,
    val user: String? = null,
    val list: String? = null,
    val durationMs: Long,
    val streams: Int? = null,
    val events: Int? = null,
    val path: String? = null,
    val status: Int? = null,
)
```

自由な `Map<String, Any>` は持たせていません。項目の説明も、payload も、`reason` も、この型に持ち場所がありません。載せないものは、書かない規律ではなく、書けないことで守られます。

利用者名とリスト名は載せます。障害の追跡に必要な識別子だからです。項目の説明は、追跡に要らない内容なので、載せません。ただし、リスト名も利用者が自分で書く内容で、個人的な情報を含みうるため、個人を特定しうる識別子です。載せる判断は人が承認しました。ログの保管とアクセスの管理は、運用で守る前提です。

### 操作を包む

操作を実行して、結果にかかわらず 1 行を書く関数が、全体の中心です。

```kotlin
private suspend fun <E : ZettaiError, A> observe(
    log: (String) -> Unit,
    now: () -> Instant,
    kind: String,
    name: String,
    user: User?,
    list: ListName?,
    streams: Int? = null,
    events: (A) -> Int? = { null },
    operation: suspend () -> Either<E, A>,
): Either<E, A> {
    val startedAt = now()

    fun write(outcome: String, eventCount: Int?) {
        val record = LogRecord(
            at = startedAt,
            kind = kind,
            name = name,
            outcome = outcome,
            user = user?.name,
            list = list?.name,
            durationMs = Duration.between(startedAt, now()).toMillis(),
            streams = streams,
            events = eventCount,
        )
        log(logLine.encoder.run(record))
    }

    val result = try {
        operation()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        write("exception:" + (failure::class.simpleName ?: "Throwable"), null)
        throw failure
    }
    write(result.fold({ it::class.simpleName ?: "Error" }, { "success" }), result.getOrNull()?.let(events))
    return result
}
```

時刻と出力先は引数です。出力先は `(String) -> Unit`、時刻は `() -> Instant` なので、テストでは時計を 25 ミリ秒ずつ進めて、所要時間まで決定的に確かめられます。1 回の操作は、`log` の 1 回の呼び出しで、1 行だけを書きます。例外が投げられたときは、例外の型名だけを `outcome` に載せた 1 行を書いて、そのまま投げ直します。メッセージとスタックトレースは載せません。出力先は、投げない契約です。

Hub は、3 つの操作をこの関数で包みます。

```kotlin
fun ToDoListHub.logged(log: (String) -> Unit, now: () -> Instant): ToDoListHub {
    val inner = this
    return ToDoListHub(
        getList = { user, listName ->
            observe(log, now, "query", "getList", user, listName) { inner.getList(user, listName) }
        },
        listRows = { user ->
            observe(log, now, "query", "listRows", user, null) { inner.listRows(user) }
        },
        execute = { command ->
            observe(
                log,
                now,
                "command",
                command::class.simpleName ?: "Command",
                command.user,
                command.listName,
                events = { it.size },
            ) { inner.execute(command) }
        },
    )
}
```

出力の例です。

```text
{"at":"2026-11-01T00:00:00Z","kind":"command","name":"CreateToDoList","outcome":"success","user":"alice","list":"book","durationMs":25,"events":1}
```

この形は、テスト `command の成功が、1 行の構造化ログになる` が固定しています。拒否も同じく 1 行で、結果の型が載ります（テスト：`command の拒否も 1 行で出る。結果の型が載る`）。

### 載せないことを確かめる

載せないものは、テストで確かめました。

- 項目の説明は載らない（テスト：`項目の説明は、ログに載らない`）
- 永続化の失敗の原因は載らない（テスト：`永続化の失敗の原因は、ログに載らない`）
- 利用者名とリスト名に引用符、バックスラッシュ、改行、絵文字があっても、改行を含まない 1 行の JSON のまま、同じ値に読み戻せる（テスト：`利用者名とリスト名に特殊文字があっても、ログは改行を含まない 1 行の JSON のまま、同じ値に読み戻せる`）

例外が投げられた操作は、型名だけの 1 行になり、メッセージは載りません。例外はそのまま投げ直されます（テスト：`操作が例外を投げたら、型名だけを載せた 1 行を書いて、そのまま投げ直す`）。coroutine のキャンセルは失敗ではないので、記録せず投げ直します（テスト：`coroutine のキャンセルは、失敗として記録せず、そのまま投げ直す`）。

decorator が結果を変えないことも、確かめました。Hub の `execute` について、任意の command 列を、decorator ありと無しの Hub に流して、結果の `Either` が一致します（テスト：`decorator を付けても、任意の command 列に対する結果は変わらない`）。並行する command でも、行は壊れません（テスト：`並行する command でも、1 回の操作は 1 回の書き込みで、行が壊れない`）。

## JSONを関数型にする

### 文字列を手で組み立てない

ログの 1 行は JSON です。値に引用符や改行が入りうるので、文字列を手で連結すると、すぐ壊れます。escape を自分で書かず、木を組み立てて、文字列にする道具が要ります。

ここで、承認済みの [ADR-040](../../../adr/ADR-040-jdbc-blocking-boundary.md) と衝突しました。ADR-040 は、「JSON ライブラリは採らない」と決めています。理由は、event の payload を SQL 側（`jsonb_build_object`）で組み立てられるからでした。ログは SQL では組めません。それに、変換の逆向き（JSON から値へ）には、parser が要ります。

用途が違うので、ADR-040 を置き換えるのではなく、用途を分けて、新しい決定を足しました（[ADR-044](../../../adr/ADR-044-json-tree-for-logs-and-converters.md)）。DB の payload は SQL 側で組むままにして、ログと変換の用途にだけ、`kotlinx-serialization-json` の `JsonElement` の木を使います。注釈と plugin は使いません。新しい依存を足すことは確認が必要なので、人の承認を得ました。

決める前に、使い捨てのスパイクで確かめました。版 1.12.0 は Kotlin 2.4.21 でコンパイルでき、引用符、バックスラッシュ、改行、タブ、日本語、絵文字、`U+0000` を含む木が、改行を含まない 1 行になり、同じ木に読み戻せました。壊れた JSON は例外になりました。

### 木と文字列の双方向変換

変換は、純粋な値として表します。

```kotlin
val jsonText: Converter<JsonElement, String> =
    Converter(
        encoder = Encoder { Json.encodeToString(JsonElement.serializer(), it) },
        decoder = Decoder { text ->
            Either.catch { Json.parseToJsonElement(text) }.fold(
                ifLeft = { ConversionFailure("JSON として読めない").left() },
                ifRight = { it.right() },
            )
        },
    )
```

壊れた JSON は、例外ではなく `Left` です。失敗の理由には、入力を含めません。入力に機密があっても、理由から漏れないためです（テスト：`壊れた JSON は例外ではなく Left になり、失敗の理由に入力を含めない`）。任意の文字列を含む木は往復し（テスト：`JSON の木と文字列の Converter は、任意の文字列を含む木を往復する`）、改行を含まない 1 行になります（テスト：`JSON の文字列にした木は、改行を含まない 1 行になる`）。

`LogRecord` と 1 行の文字列の変換は、この `jsonText` に繋げて作ります。

```kotlin
val logLine: Converter<LogRecord, String> = logRecordJson.andThen(jsonText)
```

ログを書く側が使った変換を、読む側（テスト）がそのまま使えます。

## プロファンクタとの出会い

### 入力の側と出力の側

`Encoder` は、A を B にする関数です。前に関数を置けば入力の型を変えられ（反変）、後ろに関数を置けば出力の型を変えられます（共変）。2 つを一度に行うのが `dimap` です。

```kotlin
data class Encoder<A, B>(val run: (A) -> B) {
    fun <C, D> dimap(contramap: (C) -> A, map: (B) -> D): Encoder<C, D> =
        Encoder { map(run(contramap(it))) }
}
```

読む側の `Decoder` も、同じ形です。

```kotlin
data class Decoder<B, A>(val run: (B) -> Either<ConversionFailure, A>) {
    fun <C, D> dimap(contramap: (C) -> B, map: (A) -> D): Decoder<C, D> =
        Decoder { run(contramap(it)).map(map) }
}
```

どちらも、プロファンクタの形をしています。反変は「入力を受け取る前に、別の型から写す」こと、共変は「出力を返したあとで、別の型へ写す」ことです。求める法則は 2 つです（`id` は何もしない関数、`∘` は関数の合成です）。

- `dimap id id = id`
- `dimap (f ∘ g) (h ∘ k) = dimap g h ∘ dimap f k`

法則は、property test で確かめました。`Encoder` の恒等則と合成則、`Decoder` の恒等則と合成則の 4 つです（テスト：`Encoder の dimap は恒等則を満たす`、`Encoder の dimap は合成則を満たす`、`Decoder の dimap は恒等則を満たす`、`Decoder の dimap は合成則を満たす`）。`Decoder` の失敗は、`dimap` を通しても変わりません（テスト：`Decoder の失敗は dimap で変わらない`）。

### 間違えたこと

合成則のテストは、最初に失敗しました。原因は実装ではなく、テストの法則の向きでした。右辺は、先に `dimap f k`、次に `dimap g h` です。私は逆の順に書いていました。実装は、直さずに通りました。

プロファンクタという名前は、見つけたものに名前を付けただけです。名前を付けても、実装が賢くなるわけではありません。法則が通ることだけが、名前の根拠です。

### 双方向の束には、一般の dimap が無い

2 つを束ねた `Converter` には、入力と出力を同時に写す一般の `dimap` を持たせませんでした。

```kotlin
data class Converter<A, B>(val encoder: Encoder<A, B>, val decoder: Decoder<B, A>) {
    fun <C> andThen(next: Converter<B, C>): Converter<A, C> =
        Converter(
            encoder = encoder.dimap({ it }, next.encoder.run),
            decoder = Decoder { c -> next.decoder.run(c).flatMap(decoder.run) },
        )
}
```

行きと帰りで、逆向きの関数が要るためです。`Converter` に持たせたのは、繋ぐ `andThen` です（テスト：`andThen で繋いだ Converter も往復する`）。既存 Kotlin 版は、`map` と `contramap` を持つ型を「厳密なプロファンクタではない」と断っていました。この版は、片方向ずつの `Encoder` と `Decoder` を厳密な形にして、束には一般の `dimap` を持たせない、という分け方を選びました。

`Encoder` の `dimap` の実装を壊す変更を 1 つ試したところ、型が合わず、コンパイルが通りませんでした。この変更に限れば、法則のテストが検出する前に、型が守っていました。

## データベース呼び出しのロギング

### store を包む

event store の読み出しと追記も、同じ関数で包みます。包むのは `readAggregate`、`readUser`、`readStream`、`append` で、`transaction` は包みません。`append` は、stream の数と event の件数を記録します。

```kotlin
        append = { tx, versions, events ->
            // 識別子を載せるのは、stream が 1 つのときだけ。複数あるときは、利用者だけを載せ、リスト名は載せない
            val first = versions.keys.firstOrNull()
            observe(
                log,
                now,
                "store",
                "append",
                first?.user,
                first?.listName?.takeIf { versions.size == 1 },
                streams = versions.size,
                events = { events.size },
            ) { inner.append(tx, versions, events) }
        },
```

`Tx` は総称のまま通ります。store の decorator も、`<Tx>` を持つ関数です。記録するのは、stream の数、event の件数、所要時間、結果の型だけで、payload は載せません（テスト：`payload は載らない`）。

- 追記と読み出しが、stream の数、event の件数、所要時間とともに記録される（テスト：`追記と読み出しが、stream の数、event の件数、所要時間とともに記録される`）
- 名前変更は、複数の stream への追記を 1 行で記録する（テスト：`名前変更は、複数の stream への追記を 1 行で記録する`）
- 追記の競合も記録され、結果の型が載る（テスト：`追記の競合も記録され、結果の型が載る`）

### request も記録する

HTTP の request は、filter で記録します。method、path、status、所要時間だけを載せます。query と body は載せません。

```kotlin
fun requestLogged(log: (String) -> Unit, now: () -> Instant): Filter =
    Filter { next ->
        { request ->
            val startedAt = now()
            val response = next(request)
            val finishedAt = now()
            log(
                logLine.encoder.run(
                    LogRecord(
                        at = startedAt,
                        kind = "request",
                        name = request.method.name,
                        outcome = outcomeOf(response.status),
                        durationMs = Duration.between(startedAt, finishedAt).toMillis(),
                        path = request.uri.path,
                        status = response.status.code,
                    ),
                ),
            )
            response
        }
    }
```

404、400、409、500 も、status とともに記録されます（テスト：`404、400、409、500 も、status とともに記録される`）。path に query を含める変更を入れると、`query と body は、ログに載らない` が失敗することを確かめました。

### 組み立てる

この連載には、起動する `main` がありません。組み立ては関数として示し、結合して確かめました。

```kotlin
fun <Tx> observedApp(store: EventStore<Tx>, log: (String) -> Unit, now: () -> Instant): HttpHandler {
    val hub = todoListHub(store.logged(log, now)).logged(log, now)
    return requestLogged(log, now).then(zettaiHttpApp(hub))
}
```

filter を最も外に付けるのは、`zettaiHttpApp` の `CatchAll` より外に置くためです。予期しない例外は、500 の request の行として残ります。1 回の request で、store、query、request の行が、内側から外側の順に出ます（テスト：`1 つの request で、store、query、request の行が、内側から外側の順に出る`）。原因を載せないことは、組み立てた app でも確かめました（テスト：`組み立てた app は、予期しない例外を 500 として request の行に残し、原因を載せない`）。

### 置き場所を間違えた

最初、この filter を `zettai.adapter.logging` に置きました。境界テスト `driven adapter は driving adapter と HTTP と runBlocking に依存しない` が失敗しました。http4k に依存する filter は、driven adapter に置けません。web に置けば、`LogRecord` が adapter 側にあると、web が adapter に依存してしまいます。

そこで、`LogRecord` と変換を、adapter にも web にも属さない中立の `zettai.logging` に移し、filter を web に置きました。中立の場所が他に依存しないことは、境界テストで固定しました（テスト：`ログの記録の型は、adapter と web と HTTP と JDBC に依存しない（どちらからも使える中立の場所）`）。

## まとめ

- ログは、domain にも application にも足さず、Hub と store を包む decorator と、web の filter で足しました。
- 載せるものは、`LogRecord` の型で決めました。項目の説明、payload、`reason`、スタックトレース、query、body は、持ち場所がなく、載せられません。テストで、載らないことを確かめました。
- 時刻と出力先を引数にして、ログのテストを決定的にしました。1 回の操作は 1 回の書き込みで、並行しても行が壊れません。
- JSON は、`kotlinx-serialization-json` の `JsonElement` の木だけを使いました。承認済みの ADR-040 を置き換えず、DB の payload の用途は変えずに、ログと変換の用途を足しました。人の承認を得ています。
- `Encoder` と `Decoder` は、プロファンクタの形をしています。法則を property test で確かめました。束ねた `Converter` には、一般の `dimap` を持たせていません。
- 間違えたことは 2 つです。合成則のテストの向き。filter の置き場所です。どちらも、コミットする前の作業中のことなので、履歴には残っていません。
- 型で決めたのは、載せられる項目の種類で、値の集合ではありません。`kind`、`name`、`outcome` は文字列のままです。
- 確かめていないことがあります。ログの枠組み（SLF4J など）との比較は、していません。分散トレースとメトリクスの収集は、扱っていません。相関 ID（request ID）は無く、1 回の request の複数の行は、時刻と利用者名でしか結べません。`append` の行は transaction の中で書かれるので、その後の rollback を反映せず、`success` のまま残りえます。この食い違いは、再現していません。

次の章では、ここまでの設計判断を、ひとつの見取り図として振り返ります。

## この章で書いたコード

- ログの型: `apps/kotlin-fp/zettai/zettai-step7-observability/src/main/kotlin/zettai/logging/LogRecord.kt`
- decorator: `apps/kotlin-fp/zettai/zettai-step7-observability/src/main/kotlin/zettai/adapter/logging/Logged.kt`
- request の filter: `apps/kotlin-fp/zettai/zettai-step7-observability/src/main/kotlin/zettai/web/RequestLog.kt`
- 変換: `apps/kotlin-fp/zettai/zettai-step7-observability/src/main/kotlin/zettai/json/Converter.kt`
- 法則のテスト: `apps/kotlin-fp/zettai/zettai-step7-observability/src/test/kotlin/zettai/json/ConverterLawsTest.kt`
- Hub のログのテスト: `apps/kotlin-fp/zettai/zettai-step7-observability/src/test/kotlin/zettai/adapter/logging/LoggedHubTest.kt`
- store のログのテスト: `apps/kotlin-fp/zettai/zettai-step7-observability/src/test/kotlin/zettai/adapter/logging/LoggedStoreTest.kt`
- request のログのテスト: `apps/kotlin-fp/zettai/zettai-step7-observability/src/test/kotlin/zettai/web/RequestLogTest.kt`

この章から `zettai-step7-observability` module に移ります。結合テストを含むため、Docker daemon が必要です（第 9 章を参照）。`apps/kotlin-fp/zettai/` で、次のように実行します。

Unix / Nix shell:

```text
bash ./gradlew :zettai-step7-observability:test
```

Windows PowerShell:

```text
.\gradlew.bat :zettai-step7-observability:test
```

## 参照

- Uberto Barbini『From Objects to Functions』第 12 章
- Michał Płachta『Grokking Functional Programming』Kotlin 版。関数の前後に関数を置く考え方を参照しました
- [ADR-044 ログと converter の JSON に kotlinx-serialization-json の JsonElement だけを使う](../../../adr/ADR-044-json-tree-for-logs-and-converters.md)
- [ADR-045 ログは decorator で足し、載せるものを LogRecord の型で決める](../../../adr/ADR-045-logging-as-decorator-with-typed-record.md)
