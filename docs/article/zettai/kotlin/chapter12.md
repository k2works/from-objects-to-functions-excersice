---
type: Article
title: "第 12 章 監視と関数型 JSON"
description: "Zettai 連載 Kotlin 版の第 12 章。動いているアプリケーションを外から見る手段を作る。構造化ログを 1 行 1 JSON で出し、JSON の書き出しと読み込みを 1 つの型で対にする。出力側の map と入力側の contramap の両方を持つ構造を見つけ、最後にプロファンクタという名前を与える。"
tags: [article, zettai, kotlin, chapter]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-27T05:06:57Z }
---

# 第 12 章 監視と関数型 JSON

ここまで 11 章かけて Zettai を作りました。動きます。テストも通ります。

**しかし、動いているところを外から見る手段がありません。**

この章でそれを作ります。そして 5 つめの構造が現れます。

## アプリケーションを監視する

何が困るかを確かめます。

### テストが通っても分からないこと

テストは「こう動くはず」を確かめます。しかし本番で起きることは、テストが想定していないことです。

| 知りたいこと | テストで分かるか |
| :--- | :--- |
| この操作は成功したか | いいえ。**実際の操作は本番で起きる** |
| どれくらい時間がかかったか | いいえ |
| どのリストで問題が起きたか | いいえ |
| なぜ失敗したか | いいえ |

第 7 章で `Outcome` を作り、失敗を型に載せました。**しかし失敗を「返す」だけでは、誰も見ていません。** HTTP のレスポンスとして利用者に返るだけです。

運用する人が後から追えるようにする必要があります。

### 素朴なログでは足りない

`println` を撒くのが最も簡単です。

<!-- code-check: ignore 問題を示すための擬似コード。この形を避けるのが本節の目的 -->

```kotlin
println("リストを作りました: user=uberto, list=book")
```

動きます。しかし件数が増えると困ります。

| 困ること | 内容 |
| :--- | :--- |
| 機械で集計できない | 「失敗が何件あったか」を数えるのに文字列を解析することになる |
| 形式が揃わない | 書く人によって並びも区切りも違う |
| 検索できない | 「uberto の操作」を探すのに部分一致しかできない |

**ログは人が読むものだと思いがちですが、件数が増えると機械が読むものになります。**

## 構造化されたロギング

機械が読める形にします。**1 行 1 JSON** です。

### ログの型を作る

まずログ 1 件を型にします。

```kotlin
/**
 * ログの 1 件。
 *
 * ログはドメインの外側に置く。ドメインは「記録する」ことを知らない。
 * 成功と失敗を型で分けるので、集計するときに文字列を解析しなくてよい。
 */
sealed interface LogEntry {
    val at: Instant
    val message: String
    val context: LogContext
}
```

成功と失敗を型で分けます。

```kotlin
data class LogSuccess(
    override val at: Instant,
    override val message: String,
    override val context: LogContext
) : LogEntry

data class LogFailure(
    override val at: Instant,
    override val message: String,
    override val context: LogContext,
    val reason: String
) : LogEntry
```

**`LogFailure` だけが `reason` を持ちます。** 成功に失敗の理由は要りません。型で分ければ、`reason` が null かどうかを考えずに済みます。

文脈も型にします。

```kotlin
/** ログに添える文脈。何の処理の中で起きたかを示す。 */
data class LogContext(val operation: String, val detail: Map<String, String> = emptyMap())
```

### ログはドメインの外側

**`logger` パッケージはドメインに入れません。** ドメインは「記録する」ことを知りません。

第 10 章で、文脈（`TxContext`）の中身をドメインに持ち込んで `DomainBoundaryTest` に止められました。同じ誤りを繰り返さないよう、最初から外に置きます。

ログを書く先も関数の型にします。

```kotlin
/** ログを書き出す。出力先はアダプタが決める。 */
fun interface Logger {
    fun log(entry: LogEntry)
}

/** 何も書かないログ。テストで使う。 */
val silentLogger = Logger { }
```

第 4 章から使っている「アダプタを関数の型で受け取る」形です。

### 操作を包んでログを出す

操作の前後でログを書く関数を作ります。

```kotlin
/**
 * 操作を実行し、結果をログに残す。
 *
 * 成功と失敗で別の型を書くので、集計するときに文字列を解析しなくてよい。
 */
fun <E, T> Logger.logging(context: LogContext, action: () -> Outcome<E, T>): Outcome<E, T> {
    val outcome = action()

    log(
        outcome.fold(
            { LogFailure(Instant.now(), "${context.operation} が失敗しました", context, it.toString()) },
            { LogSuccess(Instant.now(), "${context.operation} が成功しました", context) }
        )
    )

    return outcome
}
```

**`Outcome` の `fold` がここで効きます。** 成功と失敗で別の型のログを書き、結果はそのまま返します。

第 7 章で `Outcome` を作ったときは「失敗を型に載せる」ことが目的でした。5 章後に、**その型がログの出し分けに使えています。**

### JSON で書き出す

出力をアダプタにします。

```kotlin
/**
 * ログを 1 行 1 JSON で書き出す。
 *
 * JSON にするのは、後から機械で集計するため。文字列を目で読む前提のログは、
 * 件数が増えると追えなくなる。
 */
fun jsonLogger(write: (String) -> Unit): Logger = Logger { entry -> write(entry.toJson().toJsonString()) }
```

標準出力に出す実装は 1 行です。

```kotlin
/** 標準出力に書くログ。 */
fun stdoutLogger(): Logger = jsonLogger(::println)
```

**`write` を関数で受け取っているので、テストではリストに溜められます。**

```kotlin
    private val lines = mutableListOf<String>()
    private val logger = jsonLogger(lines::add)
```

ファイルに書きたければ `jsonLogger(file::appendText)` です。アダプタを関数の型にした見返りです。

### テストで確かめる

「1 行 1 JSON であること」をテストにします。

```kotlin
    @Test
    fun `1 行 1 JSON で出力される`() {
        logger.logging(LogContext("1 つめ")) { Success(1) }
        logger.logging(LogContext("2 つめ")) { Success(2) }

        expectThat(lines).hasSize(2)
        lines.forEach { line ->
            expectThat(line.contains("\n")).isEqualTo(false)
            expectThat(line.toJsonObject().fields.isNotEmpty()).isEqualTo(true)
        }
    }
```

改行が入っていないこと、そしてパースできることを確かめています。**「見た目が JSON っぽい」では足りません。**

## JSON を関数型にする

ここで、第 9 章に残した課題に戻ります。

### 第 9 章の手書きの問題

第 9 章で、出来事を JSON にする処理を手書きしました。**書き出しと読み込みが別々の関数**でした。

<!-- code-check: ignore 第 9 章時点のコード。この章で 1 つの型にまとめる -->

```kotlin
fun ToDoListEvent.toJson(): String
fun eventFrom(eventType: String, json: String): ToDoListEvent
```

問題は **片方だけ直せる**ことです。書き出しにフィールドを足して読み込みを忘れると、実行時に落ちます。

第 11 章で `ListRenamed` を足したとき、実際に両方を直しました。**忘れたら往復が壊れていました。**

### 1 つの型で対にする

書き出しと読み込みを、1 つの型に入れます。

```kotlin
/**
 * 双方向の変換。
 *
 * 書き出し（render）と読み込み（parse）を 1 つの型で対にする。
 * 片方だけ直して壊れることを防ぐ。
 *
 * 出力側は map で、入力側は contramap で変換できる。
 * 両方持つ構造をプロファンクタと呼ぶ。
 */
data class Converter<A, B>(
    val render: (A) -> B,
    val parse: (B) -> Outcome<ZettaiError, A>
) {
```

**`A` が変換元（出来事）、`B` が変換先（JSON）です。**

往復を確かめる関数も足します。

```kotlin
    /** 書き出して読み込む。往復できることを確かめるのに使う。 */
    fun roundTrip(value: A): Outcome<ZettaiError, A> = parse(render(value))
```

この 1 行が効きます。**生成した出来事で往復を試せます。**

```kotlin
    @Test
    fun `生成した出来事が往復できる`() {
        forAllRandom { random ->
            EventGenerator.events(random, random.nextInt(1, 6)).forEach { event ->
                expectThat(eventConverter.roundTrip(event)).isEqualTo(Success(event))
            }
        }
    }
```

第 5 章で作った `EventGenerator` と、同じく第 5 章由来の `forAllRandom` を使っています。**7 章前に作った道具がそのまま使えています。**

### Kondor を入れなかった

JSON ライブラリの [Kondor](https://github.com/uberto/kondor-json) を検討しました。原著の著者が作ったものです。

入れませんでした。**Kondor は独自の `Outcome` 型を持ち込みます。**

本連載は第 7 章で `Outcome` を自作しました。教材としての中心的な成果物です。Kondor を入れると同じ名前の型が 2 つ並びます。

| 選択 | 結果 |
| :--- | :--- |
| 両方持つ | 読者が混乱する |
| 変換する | 境界で相互変換するコードが増える |
| Kondor に統一する | **第 7 章を書き直すことになる** |

どれも取りたくありませんでした。判断は [ADR-012](../../../adr/ADR-012-own-json-converter.md) に記録しています。

## プロファンクタとの出会い

`Converter` には、これまでと違う性質があります。

### 出力側を変換する

`Converter<ToDoListEvent, JsonObject>` を `Converter<ToDoListEvent, String>` にしたいとします。**変換先を変える**のです。

```kotlin
    /** 出力側（B）を変換する。ファンクタの map と同じ向き。 */
    fun <C> map(to: (B) -> C, from: (C) -> B): Converter<A, C> =
        Converter(
            render = { to(render(it)) },
            parse = { parse(from(it)) }
        )
```

引数が **2 つ**あることに注目してください。`to` と `from` です。

理由は、**`Converter` が双方向だから**です。書き出しは `B → C` の向きに変換しますが、読み込みは `C → B` の向きが必要です。

実際に使っています。

```kotlin
val eventConverter: Converter<ToDoListEvent, String> =
    Converter<ToDoListEvent, JsonObject>(
        render = ::toJsonObject,
        parse = ::fromJsonObject
    ).map(to = JsonObject::toJsonString, from = String::toJsonObject)
```

**2 段になっています。** 出来事 ↔ 「名前と値の並び」の対応を書き、その上に「並び ↔ 文字列」の変換を重ねました。

直接「出来事 ↔ 文字列」を書くこともできますが、2 段にすると**それぞれが小さくなります**。

### 入力側を変換する

今度は変換元を変えます。`Converter<Int, String>` を `Converter<UserId, String>` にしたい、という場合です。

```kotlin
    /** 入力側（A）を変換する。向きが逆なので contramap。 */
    fun <C> contramap(to: (C) -> A, from: (A) -> C): Converter<C, B> =
        Converter(
            render = { render(to(it)) },
            parse = { parse(it).map(from) }
        )
```

名前が `contramap` です。**向きが逆**だからです。

`map` と見比べてください。

| | 変換する側 | `to` の向き |
| :--- | :--- | :--- |
| `map` | 出力（`B`） | `B → C` |
| `contramap` | 入力（`A`） | **`C → A`** |

`map` は「`B` を受け取って `C` を返す関数」を渡します。`contramap` は「**`C` を受け取って `A` を返す関数**」を渡します。逆です。

理由は、`A` が**入力の位置**にあるからです。`render: (A) -> B` の `A` は引数です。引数の型を `C` に変えるには、`C` から `A` を作る関数が必要です。

**入力の位置にある型は、変換の向きが逆になります。**

### 4 つの性質

`map` と `contramap` に、それぞれ 2 つの性質があります。**合わせて 4 つ**です。

**性質 1: 何もしない変換を渡したら、何も変わらない。**

```kotlin
    @Test
    fun `出力側の恒等則が成り立つ`() {
        forAllRandom { random ->
            val value = random.nextInt(-1000, 1000)
            val mapped = intToText.map(to = { it }, from = { it })

            expectThat(mapped.render(value)).isEqualTo(intToText.render(value))
            expectThat(mapped.parse(value.toString())).isEqualTo(intToText.parse(value.toString()))
        }
    }
```

**性質 2: 2 回に分けても、まとめても同じ。**

```kotlin
    @Test
    fun `出力側の合成則が成り立つ`() {
        forAllRandom { random ->
            val value = random.nextInt(-1000, 1000)

            // 2 回に分けて変換する（String → 括弧つき → さらに印つき）
            val twice = intToText
                .map(to = { "($it)" }, from = { it.removeSurrounding("(", ")") })
                .map(to = { "[$it]" }, from = { it.removeSurrounding("[", "]") })

            // まとめて 1 回で変換する
            val once = intToText.map(
                to = { "[($it)]" },
                from = { it.removeSurrounding("[", "]").removeSurrounding("(", ")") }
            )

            expectThat(twice.render(value)).isEqualTo(once.render(value))
            expectThat(twice.roundTrip(value)).isEqualTo(once.roundTrip(value))
        }
    }
```

入力側も同じ 2 つを確かめます。**合計 4 つの法則**です。

テストの形は第 5・7・9・11 章と同じ `forAllRandom` です。**5 回目です。**（第 7 章では `Outcome` 専用のラッパー `repeatWithRandomOutcomes` 越しに使っています。）

### 名前を与える

整理します。

1. ある型が**2 つの型引数**を持つ（`Converter<A, B>`）
2. 出力側（`B`）を変換する操作がある（`map`）
3. **入力側（`A`）を変換する操作がある**（`contramap`）
4. どちらも恒等則と合成則を満たす

**この構造をプロファンクタと呼びます。**

「プロ」は「前の」という意味ではなく、**反変（contravariant）**を表します。出力側は共変（そのままの向き）、入力側は反変（逆の向き）です。両方持つのでプロファンクタです。

### 身の回りのプロファンクタ

**関数そのものがプロファンクタです。**

```text
(A) -> B
```

戻り値 `B` は `map` で変換できます。引数 `A` は `contramap` で変換できます。

| 型 | map | contramap |
| :--- | :--- | :--- |
| `(A) -> B` | 戻り値を変換 | 引数を変換 |
| `Converter<A, B>` | 出力側を変換 | 入力側を変換 |
| `Comparator<A>` | — | **比較する型を変換**（`comparing` で別の型から鍵を取る） |

`Comparator` は分かりやすい例です。`Comparator<Int>` があれば、`contramap` で `Comparator<String>`（文字列の長さで比べる）が作れます。Kotlin の `compareBy { it.length }` が実質それです。

**知っていたことに名前が付いた**のは、5 回目も同じです。

ひとつ正確に書いておきます。**厳密なプロファンクタより、この `Converter` は制約が強いです。** 厳密には `dimap(f: (C) -> A, g: (B) -> D)` のように片方向の関数だけを要求します。一方 `Converter` の `map`・`contramap` は**両方向の関数を対で**要求します（`A` が `render` の入力と `parse` の出力の両方に現れるからです）。だから型としては「両方向の変換を持つもの」で、プロファンクタの形を借りていると読むのが正確です。

**名前は当てはめる前に、どこまで当てはまるかを確かめる。** 4 つの法則を確かめたのはそのためでした。

### 5 つの構造を並べる

これで 5 つ出揃いました。

| 構造 | 持つもの | 型引数 | 章 |
| :--- | :--- | :--- | :--- |
| モノイド | 合成と単位元 | 1 | 5 |
| ファンクタ | `map` | 1 | 7・8 |
| アプリカティブ | `map` + `combine` | 1 | 11 |
| モナド | `map` + `flatMap` + `pure` | 1 | 9 |
| **プロファンクタ** | `map` + `contramap` | **2** | 12 |

**プロファンクタだけ型引数が 2 つです。** 入力と出力の両方を持つので、変換の向きが 2 つあります。

次の章で、この 5 つをいつ選ぶかを整理します。

## データベース呼び出しのロギング

最後に、データベースの操作をログに残します。

### ポートの型を変えない

ここで方針を決めました。**ポートの型を変えません。**

第 7 章で `Outcome` を導入したとき、ポートの型を変えて 6 ファイルが変わりました。第 10 章で `HubAction` にしたとき、7 ファイル変更 + 3 ファイル新設でした。

**2 回続けて型を変えたので、3 回目は変えずに済む方法を探しました。**

ログは**横断関心事**です。どの操作にも共通して足せるものなので、包むだけで入ります。

```kotlin
fun <CTX, T> ContextReader<CTX, T>.logged(logger: Logger, context: LogContext): ContextReader<CTX, T> =
    ContextReader { ctx ->
        val result = runWith(ctx)

        logger.log(LogSuccess(Instant.now(), "${context.operation} を実行しました", context))

        result
    }
```

**戻り値の型が同じです。** `ContextReader<CTX, T>` を受け取って `ContextReader<CTX, T>` を返します。

だから呼び出し側は、ログを足すかどうかを選べます。

```text
PostgresEventStore.readAll()                           // ログなし
PostgresEventStore.readAll().logged(logger, context)   // ログあり
```

**型が変わらないので、既存のコードは 1 行も変わりません。**

### 包めるのは「結果を読まない」範囲まで

ここで気をつけることがあります。**この `logged` は結果の中身を読めません。**

書いているのは `LogSuccess`（「実行しました」）だけです。操作が失敗しても `LogSuccess` になります。

理由は型に出ています。`T` が何なのかを知らないから、型を変えずに包めました。**知らないものは読めません。**

成功と失敗を書き分けたいなら、`T` が `Outcome` であることを知る必要があります。

```kotlin
fun <CTX, E, T> ContextReader<CTX, Outcome<E, T>>.loggedOutcome(
    logger: Logger,
    context: LogContext
): ContextReader<CTX, Outcome<E, T>> =
    ContextReader { ctx -> logger.logging(context) { runWith(ctx) } }
```

**戻り値の型は変わっていません。** 変わったのは**要求する型**です。`ContextReader<CTX, T>` ではなく `ContextReader<CTX, Outcome<E, T>>` を要求します。

だから「横断関心事は包める」は、次の範囲での話でした。

| ログの内容 | 包めるか | 必要な知識 |
| :--- | :--- | :--- |
| 「実行した」 | 包める | なし（`logged`） |
| 「成功した / 失敗した」 | 包めるが、**型を知る必要がある** | 中身が `Outcome` であること（`loggedOutcome`） |

**横断関心事であっても、結果を読むなら型を知る必要があります。** この区別を書いておかないと、ログが全部「成功」として残ります。

### 横断関心事の見分け方

「包むだけで済むか」は、次で見分けられます。

| 問い | 包める | 型を変える必要がある |
| :--- | :--- | :--- |
| 戻り値の意味が変わるか | いいえ | **はい** |
| 呼び出し側が結果の扱いを変えるか | いいえ | **はい** |

第 7 章の `Outcome` は、**戻り値の意味が変わりました**（「値」から「値か失敗」へ）。だから呼び出し側が `fold` を書く必要があり、型を変えるほかありませんでした。

ログは戻り値の意味を変えません。だから包めます。

**設計を変えるとき、「これは横断関心事か」を先に問うと、波及の大きさが見積もれます。**

### 扱わなかったこと

第 11 章の HTML エスケープと同じ形の限界が、この章にもあります。

| 扱わなかったこと | 何が起きるか | 実務では |
| :--- | :--- | :--- |
| **JSON のエスケープ** | 値に `"` や改行が入ると、壊れた JSON を書き出す。読み戻すと値が静かに欠ける | 既製のライブラリを使う。自前で持つなら書き出しと読み込みの両方でエスケープする |

**この `Converter` をそのまま実務に持ち込めません。** 保存するのが自分のアプリだけなら今は壊れませんが、`"` を含む名前を付けた瞬間に壊れます。

黙って壊れるのを避けるため、**テストに書いておきました**。

```kotlin
    @Test
    fun `引用符を含む説明は往復できない（エスケープを扱っていない）`() {
        val event = ItemAdded(User("uberto"), ListName("book"), ToDoItem("""say "hi""""))

        expectThat(eventConverter.roundTrip(event)).isNotEqualTo(Success(event))
    }
```

「まだできないこと」をテストに書くと、直したときにこのテストが落ちます。**限界の場所が、直す合図になります。**

この判断は [ADR-012](../../../adr/ADR-012-own-json-converter.md) の「失うもの」に記録し、[第 13 章](chapter13.md) の「扱わなかったこと」にも挙げています。

## まとめ

この章でやったことを振り返ります。

- **構造化ログを作りました。** 1 行 1 JSON。成功と失敗を型で分けたので、集計に文字列の解析が要りません
- **ログはドメインの外側に置きました。** 第 10 章で文脈の中身をドメインに持ち込んで止められた誤りを、繰り返さないようにしました
- **JSON の書き出しと読み込みを 1 つの型にしました。** 片方だけ直して壊れることがなくなり、往復をランダムな入力で確かめられます
- **Kondor を入れませんでした。** 独自の `Outcome` を持ち込むので、第 7 章の成果物と並びます
- **4 つの法則を確かめてから、プロファンクタという名前を与えました。** 関数そのものと `Comparator` が同じ構造です。5 回目も「知っていたことに名前が付いた」形になりました
- **ポートの型を変えずにログを入れました。** ログは横断関心事なので包むだけで済みます。**戻り値の意味が変わるかどうか**が、包めるか型を変えるかの分かれ目でした

これで 5 つの構造が揃いました。次の章で、13 章で積み上げたものを総括します。**そして扱わなかったことも書きます。**

---

## この章で書いたコード

- 双方向変換: `apps/kotlin/zettai/zettai-step5-monitoring/src/main/kotlin/zettai/fp/Converter.kt`
- JSON: `.../json/`（`JsonValue.kt`・`EventConverter.kt`）
- ログ: `.../logger/`（`LogEntry.kt`・`Logger.kt`・`JsonLogger.kt`・`LoggedAction.kt`）
- テスト: `.../test/kotlin/zettai/json/`（`EventConverterTest.kt`・`ConverterProfunctorTest.kt`）、`.../logger/LoggerTest.kt`

この章から `zettai-step5-monitoring` モジュールに移ります。

## 参照

- Uberto Barbini『From Objects to Functions』第 12 章。構造化ロギングと双方向変換からプロファンクタに至る流れは本書に拠っています。本連載のコードはすべて書き起こした自作実装です
- [ADR-012 JSON の変換を自前の `Converter` で書き Kondor を導入しない](../../../adr/ADR-012-own-json-converter.md)
