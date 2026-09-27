---
type: Article
title: "第 7 章 関数型手法によるエラーハンドリング"
description: "Zettai 連載 Kotlin 版の第 7 章。第 2 章から持ち越した null によるエラー表現を Outcome に置き換える。失敗の理由を型で区別し、map が満たす法則を確かめてファンクタという名前を与える。ポートの型が変わるという前提の崩れが実際に何ファイルに波及したかを測った記録も含む。"
tags: [article, zettai, kotlin, chapter]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-27T00:56:59Z }
---

# 第 7 章 関数型手法によるエラーハンドリング

第 2 章で、こう書きました。

> 「リストが無い」ことを `null` で表しているのが気になるかもしれません。気になって正解です。**null は「なぜ無いのか」を説明しません。**

5 章分持ち越しました。この章で片付けます。

## より適切なエラー処理

まず、今どれだけ困っているかを確かめます。

### 失敗を数える

現在 `null` や「空のリスト」で表している失敗を列挙します。

| 失敗 | 現在の表現 | 区別できるか |
| :--- | :--- | :--- |
| リストが見つからない | `ToDoListFetcher` が `null` | 理由が分からない |
| リストが既にある（作成の拒否） | `handle` が空のリスト | **できない** |
| 項目が見つからない | `handle` が空のリスト | **できない** |
| 許されない状態遷移 | `handle` が空のリスト | **できない** |

第 6 章で `handle` が「空のイベントリスト」で拒否を表していました。呼び出し側から見ると、**「何も起きなかった」と「拒否された」が同じ**です。

そして HTTP アダプタは、すべての失敗を 404 で返していました。利用者からすると「リストが無い」と「その操作は許されない」が同じ画面に見えます。

### 選択肢を並べる

失敗をどう表すか。4 つの案を検討しました。

| 案 | 評価 |
| :--- | :--- |
| 例外を投げる | 型に現れない。呼び出し側が無視できる。関数合成もできない |
| Kotlin の `Result<T>` | 失敗が `Throwable` に固定される。業務上の失敗を閉じた型で表せない |
| ライブラリの `Either`（Arrow など） | 依存が増える。そして**自分で作るのがこの章の主題** |
| **自分で作る** | **採用** |

判断の経緯は [ADR-006](../../../adr/ADR-006-outcome-port-type.md) に記録しました。

## ファンクタと圏を学ぶ

作るものを決める前に、構造を見ます。第 5 章と同じ進め方です。名前は後に回します。

### 箱に入れる

失敗しうる計算を「箱」と考えます。箱の中には値が入っているか、入っていない（理由が入っている）。

```text
Success(ToDoList(...))     成功。値が入っている
Failure(ListNotFound(...)) 失敗。理由が入っている
```

問題は、箱の中の値を使いたいときです。毎回「成功か失敗か」を確かめると、こうなります。

<!-- code-check: ignore 説明のための擬似コード。この形を避けるのが本章の目的 -->

```kotlin
val a = fetchList(user, listName)
if (a is Failure) return a
val b = transform(a.value)
if (b is Failure) return b
val c = render(b.value)
```

これは `null` チェックの連鎖と同じ形です。**箱の存在が、やりたいことの邪魔をしています。**

### 箱の中だけを変える

別の考え方があります。**箱を開けずに、中身だけを変える。**

```text
箱の中の値に f を適用して、結果を箱に戻す
```

成功なら中身に `f` を適用します。失敗なら何もせず、そのまま流します。

```kotlin
    /** 成功なら値を変換する。失敗ならそのまま流す。 */
    fun <U> map(f: (T) -> U): Outcome<E, U> =
        when (this) {
            is Success -> Success(f(value))
            is Failure -> this
        }
```

**呼び出し側は失敗を意識しません。** `f` は成功したときの処理だけを書きます。失敗の伝搬は `map` が面倒を見ます。

### 2 つの性質

この `map` には性質があります。

**性質 1: 何もしない関数を渡したら、何も変わらない。**

```text
box.map { it }   は   box と同じ
```

**性質 2: 2 回に分けても、まとめても同じ。**

```text
box.map(f).map(g)   は   box.map { g(f(it)) } と同じ
```

どちらも当たり前に見えます。しかし**当たり前が成り立つことを確かめておくと、安心して組み替えられます**。たとえば「`map` を 2 回呼んでいるところを 1 回にまとめる」最適化が、結果を変えないと保証されます。

第 5 章と同じで、例では足りません。ランダムな入力で確かめます。

```kotlin
    @Test
    fun `合成則が成り立つ`() {
        repeatWithRandomOutcomes { outcome ->
            expectThat(outcome.map(f).map(g)).isEqualTo(outcome.map { g(f(it)) })
        }
    }
```

生成器は成功と失敗を混ぜます。

```kotlin
    fun outcome(random: Random): Outcome<ZettaiError, Int> =
        if (random.nextBoolean()) {
            Success(random.nextInt(-100, 100))
        } else {
            Failure(errors.random(random))
        }
```

### 名前を与える

整理します。

1. ある型があり、その中に値を入れられる（`Outcome<E, T>`）
2. 中身に関数を適用する操作がある（`map`）
3. 何もしない関数を渡すと何も変わらない（恒等則）
4. 2 回に分けてもまとめても同じ（合成則）

**この構造をファンクタと呼びます。**

### ファンクタも既に身の回りにある

同じ構造は他にもあります。

| 型 | map | 意味 |
| :--- | :--- | :--- |
| `List<T>` | `map` | 各要素に適用する |
| `T?`（null 許容） | `?.let` | null でなければ適用する |
| `Outcome<E, T>` | `map` | 成功なら適用する |

**`List.map` は誰でも使っています。** それがファンクタだったというだけです。第 5 章のモノイドと同じで、知っていたことに名前が付きました。

「圏論」という言葉を聞いたことがあるかもしれません。ファンクタはそこから来た概念です。ただし、**圏論を学ばなくてもファンクタは使えます。** 必要なのは「恒等則と合成則を満たす `map` がある」ことだけです。

この連載では圏論に踏み込みません。第 9 章でモナドを扱いますが、そこでも同じ方針です。**法則を確かめて、使う。**

## ファンクタを使ったエラーハンドリング

`Outcome` を作ります。

```kotlin
/**
 * 成功か失敗かを表す型。
 *
 * null は「無い」ことしか表せないが、Outcome は「なぜ無いのか」を持てる。
 * 呼び出し側は失敗を無視できない。map で繋ぐと、失敗はそのまま流れていく。
 */
sealed interface Outcome<out E, out T> {
```

`out` が付いているのは共変にするためです。`Success<T>` は `Outcome<Nothing, T>`、`Failure<E>` は `Outcome<E, Nothing>` として扱えます。

```kotlin
data class Success<T>(val value: T) : Outcome<Nothing, T>

data class Failure<E>(val error: E) : Outcome<E, Nothing>
```

### 失敗の理由を型にする

`ZettaiError` を作ります。

```kotlin
/**
 * Zettai で起きうる失敗。
 *
 * 「見つからない」で済ませず、理由を型で区別する。
 * 区別できないと、利用者に何を伝えればいいか決められない。
 */
sealed interface ZettaiError {
    val message: String
}

data class ListNotFound(override val message: String) : ZettaiError

data class ListAlreadyExists(override val message: String) : ZettaiError

data class ItemNotFound(override val message: String) : ZettaiError

data class InvalidTransition(override val message: String) : ZettaiError
```

`sealed` にしているので、`when` で全種類を扱ったかコンパイラが確かめます。**失敗を 1 種類足したら、扱い忘れている場所がコンパイルエラーになります。**

### 共変で踏んだ失敗

`orElse`（失敗なら代わりの値を返す）を、最初はメンバー関数で書きました。

<!-- code-check: ignore 実行時に失敗した書き方。現在のコードには存在しない -->

```kotlin
fun orElse(default: @UnsafeVariance T): T =
    when (this) {
        is Success -> value
        is Failure -> default
    }
```

`T` は共変（`out T`）なので、引数の位置には使えません。`@UnsafeVariance` を付けるとコンパイルは通ります。

**実行すると `ClassCastException` が出ました。**

理由はこうです。`Failure<E>` は `Outcome<E, Nothing>` なので、そこでは `T = Nothing` です。コンパイラは引数を `Nothing` にキャストするコードを生成します。`Nothing` にキャストできる値は存在しません。

`@UnsafeVariance` は名前のとおり unsafe でした。拡張関数に切り出して解決しました。

```kotlin
/**
 * 失敗なら代わりの値を返す。
 *
 * メンバー関数ではなく拡張関数にしている。共変な T を引数に取ると
 * @UnsafeVariance が必要になり、Failure（T = Nothing）で
 * 実行時に ClassCastException が起きる。実際に踏んだ。
 */
fun <E, T> Outcome<E, T>.orElse(default: T): T =
    when (this) {
        is Success -> value
        is Failure -> default
    }
```

拡張関数なら `T` は呼び出し側で決まるので、`Nothing` に固定されません。

**テストがあったので実行時に気づけました。** コンパイルが通ったから正しい、とは限りません。

## Outcome を使って実装する

ここからが本題です。**ポートの型を変えます。**

### 前提が崩れる

第 2 章から、こう書いてきました。

> `Zettai` が依存しているのは `ToDoListFetcher = (User, ListName) -> ToDoList?` という関数の型で、インターフェースではありません。第 9 章で永続化に差し替えても `Zettai` は変わりません。

今回は**型そのものが変わります**。

```kotlin
/**
 * ToDo リストを取り出す。
 *
 * 第 7 章で戻り値を ToDoList? から Outcome に変えた。
 * 「見つからない」以外の失敗を表せなかったため。
 */
typealias ToDoListFetcher = (User, ListName) -> Outcome<ZettaiError, ToDoList>
```

「型が変わらなければ上は変わらない」と言ってきた前提が、効きません。**どこまで波及するのか、実際に測りました。**

### 段階的に置き換える

一度に全部変えず、次の順で進めました。各段階で `./gradlew check` を green に保ちます。

1. `Outcome` と `ZettaiError` を作る（既存コードに影響しない）
2. ポートの型を変える
3. ハブを追従させる
4. インメモリのアダプタを追従させる
5. HTTP アダプタを追従させる
6. テストと受け入れテストの経路を追従させる

### 結果: 6 ファイル

変更が必要だったファイルは **6 個**でした。

| ファイル | 変更の内容 |
| :--- | :--- |
| `domain/ToDoListHub.kt` | ポートの型定義、`getList` と `handle` の戻り値 |
| `web/InMemoryToDoListFetcher.kt` | `null` を `Failure` に |
| `web/Zettai.kt` | `?:` を `fold` に。404 / 400 の振り分けを追加 |
| `domain/ToDoListHubTest.kt` | 期待値を `Outcome` に |
| `ddt/DomainOnlyActions.kt` | 戻り値の取り出しを `fold` に |
| `ddt/HttpActions.kt` | 同上 |

そして、**変わらなかったものを挙げます。**

| 変わらなかったもの | 理由 |
| :--- | :--- |
| ドメインの型（`ToDoList`・`ToDoItem`・`ToDoStatus`） | ポートを知らない |
| イベントと状態の畳み込み（第 5 章） | ポートを知らない |
| モノイドのテスト | 同上 |
| コマンドとステートマシン（第 6 章） | 引数で状態を受け取るだけ |
| 遷移表とそのテスト | 同上 |
| **受け入れテストのシナリオ（`SeeATodoListDdt`・`ToDoListOwner`）** | **実装を知らない** |

### 何が分かったか

「型が変わらなければ上は変わらない」という説明は、**条件付きでした**。正確にはこうです。

> 型が変われば、**その型を触る場所だけ**が変わる。

今回変わった 6 ファイルは、すべて `ToDoListFetcher` か `ToDoListHub` を直接触る場所です。ドメインの中核と受け入れテストのシナリオには波及しませんでした。

**波及しなかったのは、設計が偉いからではありません。** ドメインがポートを知らず、シナリオが実装を知らないからです。もし `ToDoList` 自体が `Outcome` を持つ設計だったら、ドメイン全体に波及していました。

これは第 3 章で受け入れテストを業務の言葉で書いた見返りでもあります。シナリオが「HTTP で GET して 200 を確かめる」と書いてあったら、型の変更で書き直しになっていました。

### HTTP で失敗を区別する

最後に、失敗の種類に応じてステータスコードを返します。

```kotlin
/**
 * 失敗の種類に応じてステータスコードを決める。
 *
 * null だった頃は、すべての失敗が 404 だった。
 * 型で区別できるようになったので、利用者に伝える内容を変えられる。
 */
private fun toResponse(error: ZettaiError): Response =
    when (error) {
        is ListNotFound, is ItemNotFound -> Response(Status.NOT_FOUND).body(error.message)
        is ListAlreadyExists, is InvalidTransition -> Response(Status.BAD_REQUEST).body(error.message)
    }
```

`when` に `else` がありません。`ZettaiError` が `sealed` なので、**全種類を扱ったことをコンパイラが確かめています**。失敗を 1 種類足したら、ここがコンパイルエラーになります。

ハンドラ側は `fold` で分岐します。

```kotlin
        return hub.getList(user, listName)
            .fold(::toResponse) { Response(Status.OK).body(renderHtml(it)) }
```

`?:` による分岐が消えました。成功と失敗の両方を 1 つの式で書いています。

### 拒否も失敗になった

第 6 章で「空のイベントリスト」で表していた拒否も、`Outcome` にしました。

```kotlin
    fun handle(command: ToDoListCommand): Outcome<ZettaiError, List<ToDoListEvent>> {
        val events = handle(command, fetchState())

        return if (events.isEmpty()) {
            command.rejected()
        } else {
            persist(events)
            events.asSuccess()
        }
    }
```

これで「何も起きなかった」と「拒否された」が区別できます。

## まとめ

この章でやったことを振り返ります。

- **失敗を数えました。** `null` と「空のリスト」で 4 種類の失敗を表していて、どれも区別できませんでした
- **箱の中だけを変える操作を作りました。** `map` は成功なら中身に適用し、失敗はそのまま流します。呼び出し側は失敗を意識しません
- **恒等則と合成則を確かめてから、ファンクタという名前を与えました。** `List.map` も同じ構造です。圏論に踏み込まなくても、法則を確かめて使えます
- **`@UnsafeVariance` で実行時例外を踏みました。** コンパイルが通ったから正しいとは限りません。テストがあったので気づけました
- **ポートの型を変え、波及範囲を測りました。** 6 ファイル。ドメインの中核と受け入れテストのシナリオには波及しませんでした

最後の点について、正直に書いておきます。**「型が変わらなければ上は変わらない」という説明は条件付きでした。** 正確には「型が変われば、その型を触る場所だけが変わる」です。

そして波及しなかったのは、設計が優れているからではなく、**ドメインがポートを知らず、シナリオが実装を知らないから**です。関数の型を使ったこと自体の効果ではありません。インターフェースで書いていても、同じ境界を引いていれば同じ結果になったはずです。

次の章では、クエリ側を分離します。ここまでは「状態を取り出して表示する」形でしたが、表示のためのモデルを別に作ります。そこで CQRS に到達します。

---

## この章で書いたコード

- `Outcome`: `apps/kotlin/zettai/zettai-step2-domain/src/main/kotlin/zettai/fp/Outcome.kt`
- 失敗の型: `.../fp/ZettaiError.kt`
- ハブ（変更）: `.../domain/ToDoListHub.kt`
- HTTP アダプタ（変更）: `.../web/Zettai.kt`
- テスト: `.../test/kotlin/zettai/fp/OutcomeTest.kt`、`OutcomeFunctorTest.kt`

## 参照

- Uberto Barbini『From Objects to Functions』第 7 章。`Outcome` という名前と、ファンクタによるエラーハンドリングという考え方は本書に拠っています。本連載のコードはすべて書き起こした自作実装です
- [ADR-006 失敗を `Outcome` で表しポートの型を変える](../../../adr/ADR-006-outcome-port-type.md)
