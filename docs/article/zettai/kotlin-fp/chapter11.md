---
type: Article
title: "第 11 章 アプリカティブによるデータバリデーション"
description: "Zettai Kotlin FP 版の第 11 章。ToDo リストの名前変更を、旧い stream と新しい stream の event で表す。複数の入力の誤りを最初の失敗で止めずにすべて集める検証を、Either.zipOrAccumulate で作り、蓄積する合成が満たす性質を property test で確かめる。自前の小さなテンプレート関数で誤りを画面に並べ、予期しない例外の本文から内部情報を取り除く。"
tags: [article, zettai, kotlin, functional-programming, arrow, applicative, validation]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-10-31T11:37:33Z }
---

# 第 11 章 アプリカティブによるデータバリデーション

前章で、複数の stream への書き込みを 1 つの transaction で確定させる契約を作りました。この章では、その契約の最初の使い手として、ToDo リストの名前変更を作ります。あわせて、利用者が入力した名前の誤りを、最初の 1 つで止めずに、すべてまとめて知らせます。

## ToDo リストの名前変更

### 名前変更は 2 つの stream に触れる

event store は、`(User, ListName)` ごとに event の列（stream）を分けています。リストの名前を変えると、旧い名前の stream では「リストが無くなる」、新しい名前の stream では「リストが作られる」ことになります。

名前変更は、次の組の event で表します。

| stream | 追記する event |
| :--- | :--- |
| 旧い名前 | `ListRenamed(user, 旧い名前, 新しい名前)` |
| 新しい名前 | `ListCreated(user, 新しい名前)` と、各項目の `ItemAdded` |

command は、触れる stream を 2 つ示します。

```kotlin
data class RenameToDoList(
    override val user: User,
    override val listName: ListName,
    val newName: ListName,
) : ToDoListCommand {
```

判断は、これまでの command と同じ形の純粋な関数です。

```kotlin
            is RenameToDoList -> {
                val list = ensureNotNull(state.listFor(command.user, command.listName)) {
                    ListNotFound(command.user, command.listName)
                }
                ensure(state.listFor(command.user, command.newName) == null) {
                    ListAlreadyExists(command.user, command.newName)
                }
                listOf(
                    ListRenamed(command.user, command.listName, command.newName),
                    ListCreated(command.user, command.newName),
                ) + list.items.map { ItemAdded(command.user, command.newName, it) }
            }
```

元のリストが無ければ `ListNotFound`、変名先がすでにあれば `ListAlreadyExists` です。同じ名前への変更は、変名先がすでにあるので拒否されます（テスト：`同じ名前への変更は、変名先がすでにあるので拒否される`）。

状態への適用は、旧い名前のキーを消すだけです。新しい名前のリストは、続く `ListCreated` と `ItemAdded` が作ります。

```kotlin
        is ListRenamed -> remove(event.key())
```

項目の状態（`Done` など）は、`ItemAdded` が項目ごと運ぶので、引き継がれます（テスト：`名前を変えると、旧い stream に ListRenamed、新しい stream に作成と項目の引き継ぎを返す`）。名前を変えて元に戻しても、項目は二重になりません（テスト：`名前を変えて戻しても、項目が二重にならない`）。

2 つの stream への書き込みが、片方だけ残ることはありません。それは前章の契約が保証します。名前変更の側では、`streams` に 2 つの stream を示すだけです。

## ２つのパラメータを持った検証

### 入力の誤りは 1 つとは限らない

（この見出しは、原書の章立てに合わせたものです。実装では、入力は 3 つです。）

名前変更の入力は、利用者名、リスト名、新しい名前の 3 つです。それぞれに規則があります。

| 規則 | 誤り |
| :--- | :--- |
| 空でない | `Blank` |
| 40 文字以内 | `TooLong` |
| `/` と制御文字を含まない | `ForbiddenCharacter` |

最初の誤りで止まる検証は、`flatMap` で繋ぎます。`flatMap` は前の結果を使って次を決める型なので、前が失敗すれば次は実行されません。

検証は、前の結果を使いません。空かどうかと、長さと、使えない文字は、独立です。独立した検証が、1 つ失敗した途端に止まる理由はありません。テストで、止まる様子を確かめました（テスト：`同じ規則を flatMap で繋ぐと、最初の失敗で止まり、2 つ目の誤りが見えない`）。

## バリデーションを使った検証

### 誤りを集める合成

誤りを集めるには、失敗した側の誤りを捨てずに、左から順に連結します。Arrow の `Either.zipOrAccumulate` が、これをします。連結の方法を渡して、独立した結果を合成します。次は 2 つの結果を合成する形で、3 つを合成する形も同じ書き方で用意しています（`Either.zipOrAccumulate({ x, y -> x + y }, first, second, third, transform)`）。

```kotlin
fun <P, A, B, R> zipAll(
    first: Either<NonEmptyList<P>, A>,
    second: Either<NonEmptyList<P>, B>,
    transform: (A, B) -> R,
): Either<NonEmptyList<P>, R> =
```

誤りの側は `NonEmptyList` です。失敗しているなら、誤りは必ず 1 つ以上あります。空の誤りの失敗を、型が許しません。

1 つの名前の 3 つの規則は、この合成で 1 つにします。

```kotlin
fun validateName(field: InputField, raw: String): Either<NonEmptyList<FieldProblem>, String> =
    zipAll(notBlank(field, raw), withinLength(field, raw), noForbiddenCharacter(field, raw)) { _, _, _ -> raw }
```

`/` を 41 個並べた名前は、`TooLong` と `ForbiddenCharacter` の両方を、規則の順に返します（テスト：`1 つの入力の複数の規則の誤りを、規則の順にすべて集める`）。複数の入力にまたがる誤りも、入力の順に集めます（テスト：`複数の入力にまたがる誤りを、入力の順にすべて集める`）。

名前変更の command は、検証に成功したときだけ作ります。

```kotlin
fun validateRename(rawUser: String, rawList: String, rawNewName: String): Either<InvalidInput, RenameToDoList> =
    validateNames(rawUser, rawList, rawNewName)
        .map { (user, list, newName) -> RenameToDoList(User(user), ListName(list), ListName(newName)) }
        .mapLeft(::InvalidInput)
```

誤りは `InvalidInput` に入ります。`InvalidInput.message` は固定の文言で、利用者の入力の値を含みません（テスト：`InvalidInput の message に入力の値を含めない`）。

## アプリカティブファンクタの結合

### 合成が満たす性質

`zipAll` は、独立した計算を合成する、アプリカティブの形をしています。`Either` の `flatMap`（モナドの束縛）と違い、前の結果を使いません。そのかわり、すべての誤りを見ることができます。

性質は、property test で確かめました。求める性質と対応するテストは、次のとおりです。

| 求める性質 | 対応するテスト |
| :--- | :--- |
| `map` の恒等則 | `蓄積する結果の map は恒等則を満たす` |
| `map` の合成則 | `蓄積する結果の map は合成則を満たす` |
| 結合律 | `結合律：どこから合成しても、成功の値も、誤りの並びも同じ` |
| 単位元 | `単位元：成功する Unit と合成しても、結果は変わらない` |
| 順序の保存 | `どちらも失敗なら、誤りを左から順に、すべて集める` |
| 誤りの個数 | `誤りの個数は、失敗した側の個数の合計になる` |
| すべて成功なら関数の適用 | `すべて成功なら、ただの関数の適用と同じ` |

注意が 1 つあります。アプリカティブ則そのもの（準同型則など）を、一般の形でテストしたわけではありません。ここで確かめたのは、`zipAll` に求める性質です。表の対応は、則の言い換えであって、則の証明ではありません。

モナドとの違いは、1 点です。モナドは、前の結果を見て次の計算を選べます。アプリカティブは、選べないかわりに、独立した計算をすべて実行できます。

## ユーザインタフェースの改善

### 誤りを画面に並べる

名前を変える画面は、`GET /todo/{user}/{list}/rename` で入力欄を表示し、同じ URL への `POST` で受けます。

| 結果 | 応答 |
| :--- | :--- |
| 成功 | `302`。新しい名前のリスト画面へ移る（PRG） |
| 検証の誤り | `400`。誤りをすべて並べ、入力を保持して、画面を再表示する |
| 元のリストが無い | `404`。日本語の理由を添えて、同じ画面を再表示する |
| 変名先がすでにある | `400`。日本語の理由を添えて、同じ画面を再表示する。入力の誤りと同じく、利用者が入力を直せば解決するため `409` にはしない |
| 競合 | `409`。「別の操作で変更されました。開き直してから、もう一度お試しください」を添えて、同じ画面を再表示する |

`POST` の処理は、検証に成功したときだけ command を実行します。業務のルールで断られたときも、英語の内部の文言ではなく、次にすることが分かる日本語を同じ画面に出します（テスト：`変名先がすでにあれば拒否する` の scenario、`変名先がすでにあれば 400、元が無ければ 404`）。

```kotlin
        validateRename(rawUser, rawList, rawNewName).fold(
            ifLeft = { invalid ->
                html(
                    Status.BAD_REQUEST,
                    renderRenameForm(
                        User(rawUser),
                        ListName(rawList),
                        rawNewName,
                        invalid.problems.map(::describeProblem),
                    ),
                )
            },
            ifRight = { command ->
                hub.execute(command).fold(
                    ifLeft = { error ->
                        if (error is ListAlreadyExists || error is ListNotFound || error is ConcurrentModification) {
                            html(
                                statusOf(error),
                                renderRenameForm(
                                    command.user,
                                    command.listName,
                                    rawNewName,
                                    listOf(describeError(error)),
                                ),
                            )
                        } else {
                            toResponse(error)
                        }
                    },
                    ifRight = {
```

画面は、テンプレートから作ります。既製の template エンジンは比べていません。必要なのは、値を埋める、エスケープする、値の無いタグを見逃さない、の 3 つで、小さな関数で足りる範囲に絞りました。

```kotlin
fun renderTemplate(template: String, values: Map<String, String>): Either<UnappliedTags, String> {
    val unapplied = linkedSetOf<String>()
    val rendered = TAG.replace(template) { match ->
        val raw = match.groups[1]?.value
        val name = raw ?: match.groupValues[2]
        val value = values[name]
        when {
            value == null -> {
                unapplied += name
                match.value
            }

            raw != null -> value
            else -> escapeHtml(value)
        }
    }
    return if (unapplied.isEmpty()) rendered.right() else UnappliedTags(unapplied.toList()).left()
}

```

二重の波括弧のタグはエスケープして埋め、三重の波括弧のタグは生のまま埋めます。値の無いタグが 1 つでもあれば、画面にタグを残さず、描画を失敗にします（テスト：`埋めていないタグがあれば、名前を集めて失敗する`）。差し込んだ値の中のタグは、もう一度は解釈しません（テスト：`埋めた値の中のタグは、もう一度は解釈しない`）。

入力の保持も確かめました。`<b>` を含む入力は、エスケープされて入力欄に戻ります（テスト：`検証の誤りは 400 で、すべての理由を並べ、入力を HTML エスケープして保持する`）。

### 実装中に見つけた漏れ

画面のテストを書く途中で、予期しない例外の本文を確かめるテストを足しました。`CatchAll` の既定の応答は、例外のスタックトレースを本文に含めます。例外の message に接続文字列があれば、それも本文に出ます。第 9 章で `PersistenceFailure` の message を固定にしたのと同じ種類の漏れが、別の経路に残っていました。

このテストは、直す前に失敗しました。固定の本文を返すように直しました。

```kotlin
private fun unexpectedFailure(@Suppress("UNUSED_PARAMETER") failure: Throwable): Response =
    Response(Status.INTERNAL_SERVER_ERROR)
        .header("Content-Type", "text/plain; charset=utf-8")
        .header("X-Content-Type-Options", "nosniff")
        .body("Internal server error")
```

この確認は、テスト `予期しない例外の本文に、内部の情報を出さない` です。

## まとめ

- 名前変更を、旧い stream の `ListRenamed` と、新しい stream の `ListCreated`・`ItemAdded` の組で表しました。2 つの stream を 1 つの transaction で書く契約は、前章のものをそのまま使いました。
- 入力の誤りを、`Either.zipOrAccumulate` で止めずにすべて集めました。誤りは `NonEmptyList` で、失敗なのに誤りが空という状態を作れません。
- `flatMap` との違いを、テストで示しました。検証は前の結果を使わないので、モナドの束縛ではなく、アプリカティブの合成で足ります。
- 蓄積する合成に求める性質を、property test で確かめました。アプリカティブ則そのものを一般の形でテストしたのではありません。
- 自前の小さなテンプレート関数で、値の無いタグを描画の失敗にし、値をエスケープしました。既製の template エンジンとは比べていません。
- Cucumber の 3 経路のうち、HTTP は in-memory の store で動きます。PostgreSQL の経路は、画面を通さずに Hub を直接呼びます。HTTP と PostgreSQL の組み合わせは、この章では確かめていません。
- 実装中に見つけた漏れは 1 つです。`CatchAll` の応答が、内部の情報を本文に出していました。

## この章で書いたコード

- 名前変更の command と判断: `apps/kotlin-fp/zettai/zettai-step6-transaction-validation/src/main/kotlin/zettai/domain/commands/`
- 検証: `apps/kotlin-fp/zettai/zettai-step6-transaction-validation/src/main/kotlin/zettai/domain/validation/Validation.kt`
- テンプレート: `apps/kotlin-fp/zettai/zettai-step6-transaction-validation/src/main/kotlin/zettai/web/Template.kt`
- 画面: `apps/kotlin-fp/zettai/zettai-step6-transaction-validation/src/main/kotlin/zettai/web/HtmlPage.kt`
- 蓄積の性質のテスト: `apps/kotlin-fp/zettai/zettai-step6-transaction-validation/src/test/kotlin/zettai/domain/validation/AccumulationLawsTest.kt`
- 受け入れ scenario: `apps/kotlin-fp/zettai/zettai-step6-transaction-validation/src/test/resources/features/rename_todo_list.feature`

`apps/kotlin-fp/zettai/` で、次のように実行します。結合テストを含むため、Docker daemon が必要です。

Unix / Nix shell:

```text
bash ./gradlew :zettai-step6-transaction-validation:test
```

Windows PowerShell:

```text
.\gradlew.bat :zettai-step6-transaction-validation:test
```

## 参照

- Uberto Barbini『From Objects to Functions』第 11 章
- Michał Płachta『Grokking Functional Programming』Kotlin 版。独立した計算の合成を参照しました
