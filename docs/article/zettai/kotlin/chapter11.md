---
type: Article
title: "第 11 章 アプリカティブによるデータバリデーション"
description: "Zettai 連載 Kotlin 版の第 11 章。ToDo リストの名前変更を題材に、複数の入力の誤りをまとめて返すバリデーションを作る。モナドが最初の失敗で止まる理由を示し、止まらない構造を見つけて最後にアプリカティブという名前を与える。未適用のタグを失敗として扱うテンプレート機構も自前で書く。"
tags: [article, zettai, kotlin, chapter]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-27T03:03:00Z }
---

# 第 11 章 アプリカティブによるデータバリデーション

第 7 章で失敗を型に載せました。ただし返るのは **最初の 1 つだけ**です。

この章で、まとめて返せるようにします。この連載で 4 つめの構造が現れます。

## ToDo リストの名前変更

新しいストーリーです。

> 利用者として、ToDo リストの名前を変えたい。なぜなら、用途が変わることがあるからだ。

コマンドとイベントを足します。第 6 章で作った形をそのまま使います。

```kotlin
data class RenameToDoList(
    override val user: User,
    override val listName: ListName,
    val newName: ListName
) : ToDoListCommand
```

```kotlin
data class ListRenamed(
    override val user: User,
    override val listName: ListName,
    val newName: ListName
) : ToDoListEvent
```

コマンドの処理も同じ形です。

```kotlin
        is RenameToDoList ->
            if (state.listFor(command.user, command.listName) == null ||
                state.listFor(command.user, command.newName) != null
            ) {
                emptyList()
            } else {
                listOf(ListRenamed(command.user, command.listName, command.newName))
            }
```

**5 章前に作った形に、新しい操作を足すだけで済んでいます。** これが第 6 章で「業務ルールが 1 箇所に集まる」と書いたことの見返りです。

### 入力を検証する必要が出た

ここまでのコマンドは、引数がすでに型になっていました（`ListName`・`ToDoItem`）。名前の変更は違います。**利用者が文字列を入力します。**

確かめたいことが 2 つあります。

- 空ではないか
- 長すぎないか

## 2 つのパラメータを持った検証

素朴に、第 7 章の `Outcome` で書いてみます。

<!-- code-check: ignore 問題を示すための擬似コード。この形を避けるのが本章の目的 -->

```kotlin
fun validateListName(raw: String): Outcome<ZettaiError, ListName> =
    notBlank(raw)
        .transform { withinLength(it) }
        .map { ListName(it) }
```

動きます。しかし問題があります。

### 最初の失敗で止まる

空文字を渡すと、`notBlank` が失敗します。そして **`withinLength` は実行されません。**

`transform`（`flatMap`）の定義を見れば分かります。

```kotlin
    /** 成功なら次の Outcome に繋ぐ。失敗ならそのまま流す。 */
    fun <F, U> transform(f: (T) -> Outcome<F, U>): Outcome<Any?, U> =
        when (this) {
            is Success -> f(value)
            is Failure -> this
        }
```

失敗なら `f` を呼びません。**前の結果を使って次を決める**のがモナドなので、前が失敗したら次を決められないのです。

テストで確かめました。

```kotlin
    @Test
    fun `Outcome は最初の失敗で止まる`() {
        var secondCalled = false

        val outcome: Outcome<ZettaiError, String> = Failure(InvalidTransition("1 つめが失敗"))
            .transform<ZettaiError, String> {
                secondCalled = true
                Success("2 つめ")
            } as Outcome<ZettaiError, String>

        // 同じ「2 つの検証」を Outcome で書くと、2 つめは実行されない
        expectThat(secondCalled).isEqualTo(false)
        expectThat(outcome is Failure).isEqualTo(true)
    }
```

### 何が困るか

利用者から見ると、こうなります。

1. 空のまま送信 → 「リスト名を入力してください」
2. 50 文字入れて送信 → 「リスト名は 40 文字以内にしてください」

**1 回で済むはずのやり直しが 2 回になりました。** 入力欄が 5 つあれば 5 回です。

モナドは「前の結果を使う」ために失敗で止まります。**しかし検証では、前の結果を使いません。** 空かどうかと長すぎるかどうかは独立に確かめられます。

**止まる必要がないのに止まっている**のです。

## バリデーションを使った検証

止まらない型を作ります。

```kotlin
/**
 * 検証の結果。
 *
 * Outcome（モナド）は最初の失敗で止まる。flatMap が「前の結果を使って次を決める」
 * ので、前が失敗したら次を実行できない。
 *
 * Validation は止まらない。複数の検証を独立に走らせ、失敗を全部集める。
 * 「前の結果を使う」ことを諦める代わりに、「全部集める」ことができる。
 */
sealed interface Validation<out T> {
```

成功と失敗は `Outcome` と同じ形ですが、**失敗が複数持てます。**

```kotlin
data class Valid<T>(val value: T) : Validation<T>

data class Invalid(val errors: List<String>) : Validation<Nothing> {
    constructor(error: String) : this(listOf(error))
}
```

### 合わせる操作

肝はここです。**2 つの検証結果を合わせる操作**を作ります。

```kotlin
/**
 * 2 つの検証結果を合わせる。
 *
 * 両方成功なら f を適用する。**どちらかでも失敗なら、失敗を全部集める。**
 * これがアプリカティブの合成。
 */
fun <A, B, R> combine(a: Validation<A>, b: Validation<B>, f: (A, B) -> R): Validation<R> =
    when {
        a is Valid && b is Valid -> Valid(f(a.value, b.value))
        else -> Invalid(a.errorsOrEmpty() + b.errorsOrEmpty())
    }
```

`flatMap` との違いを見比べてください。

| | `flatMap`（モナド） | `combine`（アプリカティブ） |
| :--- | :--- | :--- |
| 引数 | 値から次の計算を作る関数 | **すでにある 2 つの結果** |
| 前が失敗したとき | 次を実行しない | **次も見る** |
| できること | 前の結果を使って次を決める | 独立な計算を合わせる |
| 失敗 | 最初の 1 つ | **全部** |

**`combine` は「すでにある 2 つの結果」を受け取ります。** 次の計算を作る関数ではありません。だから前が失敗していても、後ろを見られます。

### 3 つ以上を合わせる

2 つ版があれば、3 つ版は組み合わせで作れます。

```kotlin
/** 3 つの検証結果を合わせる。2 つ版を 2 回使う。 */
fun <A, B, C, R> combine(
    a: Validation<A>,
    b: Validation<B>,
    c: Validation<C>,
    f: (A, B, C) -> R
): Validation<R> = combine(combine(a, b) { x, y -> x to y }, c) { (x, y), z -> f(x, y, z) }
```

いくつでも増やせます。

### 使ってみる

リスト名の検証を書きます。

```kotlin
fun validateListName(raw: String): Validation<ListName> =
    combine(notBlank(raw), withinLength(raw)) { _, value -> ListName(value) }

private fun notBlank(raw: String): Validation<String> =
    if (raw.isNotBlank()) raw.asValid() else "リスト名を入力してください".asInvalid()

private fun withinLength(raw: String): Validation<String> =
    if (raw.length <= MAX_LIST_NAME_LENGTH) {
        raw.asValid()
    } else {
        "リスト名は $MAX_LIST_NAME_LENGTH 文字以内にしてください".asInvalid()
    }
```

50 個の空白を渡すと、**両方の条件に引っかかります**（空白だけなので `isNotBlank` が false、かつ 40 文字超）。

受け入れテストで確かめました。

```kotlin
    @TestFactory
    fun `不正な名前は理由をまとめて教える`() = ddtScenario {
        val uberto = ToDoListOwner("uberto")

        play(
            uberto.`creates a list`("book"),
            uberto.`is told all the problems`(
                "book",
                " ".repeat(50),
                listOf("リスト名を入力してください", "リスト名は 40 文字以内にしてください")
            )
        )
    }
```

**2 つの理由がまとめて返ります。** 利用者は 1 回で両方直せます。

## アプリカティブファンクタの結合

ここまでの構造を整理します。第 5・7・9 章と同じ進め方です。

### 4 つの性質

**性質 1・2: ファンクタの 2 つ。**

`Validation` には `map` があり、恒等則と合成則を満たします。第 7 章の `Outcome` と同じです。

```kotlin
    @Test
    fun `合成則が成り立つ`() {
        forAllRandom { random ->
            val v = ValidationGenerator.validation(random)

            expectThat(v.map(f).map(g)).isEqualTo(v.map { g(f(it)) })
        }
    }
```

**性質 3: 合わせる順序をどう括っても同じ。**

```kotlin
    @Test
    fun `combine は結合的`() {
        forAllRandom { random ->
            val a = ValidationGenerator.validation(random)
            val b = ValidationGenerator.validation(random)
            val c = ValidationGenerator.validation(random)

            val left = combine(combine(a, b) { x, y -> "$x$y" }, c) { xy, z -> "$xy$z" }
            val right = combine(a, combine(b, c) { y, z -> "$y$z" }) { x, yz -> "$x$yz" }

            expectThat(left).isEqualTo(right)
        }
    }
```

**性質 4: 失敗がひとつも失われない。**

```kotlin
    @Test
    fun `失敗はひとつも失われない`() {
        forAllRandom { random ->
            val a = ValidationGenerator.validation(random)
            val b = ValidationGenerator.validation(random)

            val combined = combine(a, b) { x, y -> "$x$y" }
            val expected = a.errorCount() + b.errorCount()

            expectThat(combined.errorCount()).isEqualTo(expected)
        }
    }
```

4 つめが `Validation` 固有です。**失敗の数が足し算になっている**ことを確かめています。1 つでも落としたら、まとめて返す意味がなくなります。

テストの形は第 5・7・9 章と同じ `forAllRandom` です。**4 回目です。**

### 名前を与える

整理します。

1. ある型があり、その中に値を入れられる（`Validation<T>`）
2. 中身に関数を適用する操作がある（`map`）— **ファンクタ**
3. **すでにある複数の結果を合わせる操作がある**（`combine`）
4. 合わせる順序をどう括っても同じ

**この構造をアプリカティブ（アプリカティブファンクタ）と呼びます。**

### 3 つの関係

第 7・9 章と並べます。

| 構造 | 持つもの | できること | 失敗の扱い |
| :--- | :--- | :--- | :--- |
| ファンクタ | `map` | 中身を変換する | — |
| **アプリカティブ** | `map` + `combine` | **独立な計算を合わせる** | **全部集められる** |
| モナド | `map` + `flatMap` + `pure` | 前の結果から次を決める | 最初の 1 つで止まる |

**モナドのほうが「強い」のに、失敗の扱いでは劣ります。** これは不思議に見えますが、理由は単純です。

「前の結果を使って次を決める」には、**前の結果が必要**です。前が失敗していたら次を決められません。だから止まります。

アプリカティブは「前の結果を使う」ことを諦めました。**その代わり、独立に走らせて全部集められます。**

**強い構造が常に良いわけではありません。** やりたいことに必要な強さを選びます。

### 身の回りのアプリカティブ

| 型 | combine 相当 | 意味 |
| :--- | :--- | :--- |
| `List<T>` | `zip` | 2 つのリストを組にする |
| `Validation<T>` | `combine` | 失敗を集める |

`List.zip` はアプリカティブの例です。**知っていたことに名前が付いた**のは、4 回目も同じです。

## ユーザインタフェースの改善

最後に画面です。第 2 章から Kotlin の文字列テンプレートで組み立ててきました。

### 何が困るか

エラーの一覧を表示するには、繰り返しが必要です。文字列テンプレートでもできますが、**書き忘れに気づけません。**

たとえばテンプレートに `{{errorMessage}}` と書いたのに、データを渡し忘れたとします。

| 方式 | 起きること |
| :--- | :--- |
| Kotlin の文字列テンプレート | コンパイルエラー（変数が無い）。**これは安全** |
| 既製のテンプレートエンジン | 空文字になるか、そのまま出力される |
| **自前の機構** | **失敗として扱える** |

Kotlin の文字列テンプレートは実は安全です。問題は**動的な繰り返しや出し分けを書きにくい**ことです。

### 既製品を確かめた

http4k には `http4k-template-handlebars` などのモジュールがあります。使えるか確かめました。

要求は 1 つです。**タグを書いたのにデータを渡し忘れたら、失敗として扱う。**

| 候補 | 未適用タグの扱い |
| :--- | :--- |
| Handlebars | 未定義の変数は**空文字**。厳格モードは例外を投げる |
| Pebble | 既定では空文字。`strictVariables` で例外 |
| Thymeleaf | 評価エラーだが例外ベース |

**どれも例外を投げる形で、`Outcome` を返しません。** ラップすれば乗せられますが、そのラッパを書くくらいなら機構全体が 80 行で書けます。

加えて、既製品を使うとテンプレートの構文の説明が要り、主題から注意が逸れます。**自前で書くことにしました**（[ADR-010](../../../adr/ADR-010-own-template.md)）。

### 自前の機構

タグは 3 種類です。

```kotlin
/** テンプレートに差し込む値。 */
sealed interface TemplateTag

data class StringTag(val text: String) : TemplateTag

data class ListTag(val rows: List<Map<String, TemplateTag>>) : TemplateTag

data class BooleanTag(val value: Boolean) : TemplateTag
```

適用の結果は `Outcome` です。

```kotlin
data class Template(val text: String) {
    /** タグを適用する。未適用のタグが残っていたら失敗。 */
    fun render(data: Map<String, TemplateTag>): Outcome<TemplateError, String> =
        applyTags(text, data).checkNoTagsLeft()
}
```

肝は最後の検査です。

```kotlin
private fun String.checkNoTagsLeft(): Outcome<TemplateError, String> {
    val left = TAG.findAll(this).map { it.groupValues[1] }.toList()

    return if (left.isEmpty()) {
        Success(this)
    } else {
        Failure(TemplateError("適用されていないタグが残っています: ${left.joinToString(", ")}"))
    }
}
```

**残ったタグの名前を挙げて失敗します。** テストで確かめました。

```kotlin
    @Test
    fun `未適用のタグの名前を教える`() {
        val result = Template("{{a}}{{b}}{{c}}").render(mapOf("a" to StringTag("あ")))

        val message = (result as Failure).error.message

        expectThat(message).contains("b")
        expectThat(message).contains("c")
    }
```

リストの中の未適用タグも検出します。

```kotlin
    @Test
    fun `リストの中の未適用タグも検出する`() {
        val result = Template("{{#items}}<li>{{name}} {{status}}</li>{{/items}}").render(
            mapOf("items" to ListTag(listOf(mapOf("name" to StringTag("write chapter")))))
        )

        expectThat(result).isA<Failure<*>>()
    }
```

### 画面に使う

ToDo リストの画面を置き換えます。

```kotlin
fun renderHtml(listName: ListName, items: List<ToDoItem>): Outcome<TemplateError, String> =
    listPage.render(
        mapOf(
            "listName" to StringTag(listName.name),
            "items" to ListTag(items.map(::itemRow))
        )
    )
```

HTTP の層では、失敗を 500 にします。

```kotlin
private fun renderPage(listName: ListName, items: List<ToDoItem>): Response =
    renderHtml(listName, items)
        .fold(
            { Response(Status.INTERNAL_SERVER_ERROR).body("画面を組み立てられません: ${it.message}") },
            { Response(Status.OK).body(it) }
        )
```

**未適用のタグは実装の誤りなので、500 です。** 利用者に壊れた画面を見せません。

### 扱わなかったこと

正直に書きます。**HTML エスケープがありません。**

利用者が入力した文字列をそのまま HTML に差し込むので、`<script>` を入れられます。**実務では必須の処理を、本連載では扱いません。**

理由は、この章の主題が「タグの適用を型で安全にすること」であり、エスケープは別の話だからです。ただし**扱わないことを黙っていると、このコードをそのまま使う人が出ます**。ここに明記しておきます。

| 扱わなかったこと | 実務では |
| :--- | :--- |
| HTML エスケープ | 必須。差し込む値をすべてエスケープする |
| CSRF 対策 | フォームを作るなら必須 |
| テンプレートの部分読み込み | 画面が増えたら欲しくなる |

## まとめ

この章でやったことを振り返ります。

- **リスト名の変更を足しました。** 第 6 章で作ったコマンドとイベントの形に、新しい操作を足すだけで済みました
- **モナドが最初の失敗で止まる理由を示しました。** `flatMap` は前の結果を使って次を決めるので、前が失敗したら次を決められません
- **止まらない型を作りました。** `combine` は「すでにある 2 つの結果」を受け取るので、前が失敗していても後ろを見られます
- **4 つの性質を確かめてから、アプリカティブという名前を与えました。** 4 つめ（失敗がひとつも失われない）が `Validation` 固有です。テストの形は 4 回目も同じでした
- **強い構造が常に良いわけではないと分かりました。** モナドは「前の結果を使う」ために止まります。アプリカティブはそれを諦めて、全部集めます
- **テンプレート機構を自前で書きました。** 既製品を確かめた結果、どれも例外ベースで `Outcome` を返しません。未適用のタグを失敗にするのが狙いでした
- **HTML エスケープを扱わないことを明記しました。** 実務では必須です

次の章では、動いているアプリケーションを外から見る手段を作ります。ログと JSON です。そこで 5 つめの構造（プロファンクタ）が現れます。

---

## この章で書いたコード

- `Validation`: `apps/kotlin/zettai/zettai-step4-context/src/main/kotlin/zettai/fp/Validation.kt`
- リスト名の検証: `.../domain/ListNameValidation.kt`
- テンプレート: `.../ui/Template.kt`
- 画面: `.../web/HtmlPage.kt`
- テスト: `.../test/kotlin/zettai/fp/ValidationTest.kt`・`ValidationApplicativeTest.kt`、`.../ui/TemplateTest.kt`

## 参照

- Uberto Barbini『From Objects to Functions』第 11 章。アプリカティブによるバリデーションと自前のテンプレート機構という構成は本書に拠っています。本連載のコードはすべて書き起こした自作実装です
- [ADR-010 テンプレート機構を自前で書き既製のテンプレートエンジンを使わない](../../../adr/ADR-010-own-template.md)
