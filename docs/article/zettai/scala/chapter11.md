---
type: Article
title: "第 11 章 アプリカティブによるデータバリデーション"
description: "Zettai 連載 Scala 版の第 11 章。リスト名の変更を足し、入力の誤りを Cats の Validated で集める。Either の連鎖との違い、アプリカティブの法則、壊れた合成が誤りを落とすことを確かめ、実サーバーで 400 の応答に誤りをまとめて返す。"
tags: [article, zettai, scala, cats-effect, validated, applicative, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-02T15:00:00Z }
---

# 第 11 章 アプリカティブによるデータバリデーション

第 7 章で、失敗を `Either` で運びました。`Either` は最初の失敗で止まります。利用者の入力に誤りが複数あるとき、1 つずつ直させるのは親切ではありません。この章では、**すべての誤りを集めて返します**。

## ToDo リストの名前変更

### 名前変更は、旧い名前の消去と、新しい名前での作成

イベントの列は、リストの名前ごとに分かれて意味を持ちます。名前を変えると、旧い名前のリストは無くなり、新しい名前のリストが、項目を引き継いで現れます。これを、イベントで表します。

| 順 | イベント | 意味 |
| :--- | :--- | :--- |
| 1 | `ListRenamed(user, 旧い名前, 新しい名前)` | 旧い名前のリストが無くなる |
| 2 | `ListCreated(user, 新しい名前)` | 新しい名前のリストが作られる |
| 3 以降 | `ItemAdded(user, 新しい名前, 項目)` | 項目が、状態ごと引き継がれる |

`ItemAdded` は項目の状態を持っているので、状態を変えるイベントを別に出す必要がありません。

```scala
  case ListRenamed(user: User, name: ListName, newName: ListName)
```

```scala
      list.items.map(item => ToDoListEvent.ItemAdded(user, newName, item))
```

コマンドとイベントを足すと、第 6 章でおなじみのことが起きました。コンパイラが、`match` の更新漏れを指摘したのです。ただし、一度に全部は出ません。1 回目は 2 か所でした。

```bash
Commands.scala:24
Events.scala:25
```

直すと、次の段階で 3 か所が出ました。

```bash
PostgresStore.scala:36
Projection.scala:12
Projection.scala:20
```

**合計 5 か所です。** Flix 版も 5 か所でした。コンパイラは、段階ごとに見つけるので、全部が一度には出ません。

もう 1 つ、コンパイラが**見つけない**場所がありました。PostgreSQL から読み戻す側は、種類を表す文字列で `match` しているため、`enum` の網羅性の検査が及びません。

```scala
      case "ListRenamed"       => ToDoListEvent.ListRenamed(user, name, ListName(row.description))
```

この行を抜くと、読み戻しのテストが `IllegalStateException`（知らない種類のイベントです: ListRenamed）で落ちました。**コンパイラが守らない境界は、往復のテストが守ります。** 全種類のイベントを書いて読み戻すテストを足しました。

```scala
  test("名前変更のイベントも含めて、すべての種類のイベントを書き戻して読み戻せる") {
```

名前変更の振る舞いは、テストで確かめました。旧い名前は消え、新しい名前のリストが状態ごと項目を引き継ぎます。

```scala
  test("適用すると、旧い名前のリストは無くなり、新しい名前のリストが項目を状態ごと引き継ぐ") {
```

```scala
  test("すでにある名前への変更は拒否する") {
```

## 2 つのパラメータを持った検証

### 入力の誤りは 1 つとは限らない

名前変更の入力は 3 つあります。利用者名、リスト名、新しい名前です。それぞれに、3 つの規則があります。

- 空にできない
- 40 文字以内
- `/` と制御文字を含めない

誤りは、値を持たない型で表します。誤りのもとになった入力を、メッセージや記録に出さないためです。

```scala
enum FieldProblem:
  case Blank(field: InputField)
  case TooLong(field: InputField, limit: Int)
  case ForbiddenCharacter(field: InputField)
```

`Either` の連鎖で書くと、最初の誤りで止まります。

```scala
  test("Either の連鎖では、最初の誤りで止まる。比べると、集めている数が違う") {
```

3 つの入力がすべて空のとき、`Either` の連鎖は最初の 1 つしか返しません。集めると 3 つになります。

```scala
    assertEquals(viaEither, Left(FieldProblem.Blank(InputField.UserName)))
    assertEquals(Validate.validateRename("", "", "").fold(_.length, _ => 0L), 3L)
```

## バリデーションを使った検証

### 誤りを集める合成

Cats の `Validated` は、`Either` と同じく成功か失敗を持ちます。違いは、**2 つの `Validated` を合成するとき、両方が失敗なら誤りを集める**ことです。誤りの入れ物には `NonEmptyChain` を使う `ValidatedNec` が便利です。

```scala
  def notBlank(field: InputField, raw: String): ValidatedNec[FieldProblem, Unit] =
    if raw.trim.isEmpty then fail(FieldProblem.Blank(field)) else ().validNec
```

1 つの入力の 3 つの規則は、互いに独立です。`mapN` で合成します。

```scala
  def validateName(field: InputField, raw: String): ValidatedNec[FieldProblem, String] =
    (notBlank(field, raw), withinLength(field, raw), noForbiddenCharacter(field, raw)).mapN((_, _, _) => raw)
```

3 つの入力も互いに独立なので、同じ形で合成します。

```scala
  def validateRename(rawUser: String, rawList: String, rawNewName: String): ValidatedNec[FieldProblem, ToDoListCommand] =
    (
      validateName(InputField.UserName, rawUser),
      validateName(InputField.ListNameField, rawList),
      validateName(InputField.NewName, rawNewName)
    ).mapN((user, list, newName) => ToDoListCommand.RenameToDoList(User(user), ListName(list), ListName(newName)))
```

誤りは、規則の順、入力の順に集まります。

```scala
  test("3 つの入力の誤りは、最初の 1 つで止めず、入力の順にすべて集める") {
```

`Validated` は `Monad` ではありません。前の結果を見て次を決める `flatMap` を持たない代わりに、独立した検証を並べて、すべての誤りを集められます。**`flatMap` を持つ型は、前の失敗で後ろを止めてしまうからです。**

## アプリカティブファンクタの結合

### 合成が満たす性質

`mapN` の土台は、アプリカティブの `ap` です。関数が入った `Validated` を、値が入った `Validated` に適用します。次の 4 つの法則が成り立ちます。

```scala
  private val A = Applicative[[X] =>> ValidatedNec[FieldProblem, X]]
```

```scala
  test("恒等則: pure(identity) を適用しても何も変わらない") {
    for v <- samples do assertEquals(A.ap(A.pure[Int => Int](identity))(v), v)
  }
```

```scala
  test("準同型則: pure(f) を pure(x) に適用すると pure(f(x))") {
    for x <- List(0, 1, 41) do assertEquals(A.ap(A.pure[Int => Int](_ + 3))(A.pure(x)), A.pure(x + 3))
  }
```

```scala
  test("交換則: 関数と値の順序を入れ替えても同じ") {
```

合成則は、関数を先に合成してから適用しても、順に適用しても同じです。**誤りの集まり方も含めて**確かめます。成功と失敗、片側の失敗、両側の失敗を試しました。

```scala
  test("合成則: 関数を先に合成してから適用しても、順に適用しても同じ（誤りの集まり方も含めて）") {
```

### 壊れた合成を見分けられるか

法則のテストが、壊れた合成を見分けられるのかを確かめます。壊れた合成は、**左の誤りだけを返し、右の誤りを捨てます**。つまり、`Either` と同じ動きです。

```scala
  private def brokenAp[A, B](ff: V[A => B])(fa: V[A]): V[B] =
```

```scala
  test("壊れた合成は、成功と片側の失敗では Validated と一致するが、両側が失敗したときに誤りを落とす") {
    assert(samples.forall(v => brokenAp(A.pure[Int => Int](identity))(v) == v))
    assertNotEquals(brokenAp(bad)(samples(3)), A.ap(bad)(samples(3)))
  }
```

**成功の側だけを見るテストは、この壊れた合成を見逃します。** 両側が失敗する入力を含めたことで、初めて区別できました。

## ユーザインタフェースの改善

### 誤りを、まとめて返す

利用者に見せる説明は、誤りの種類から作ります。入力の値は含めません。

```scala
  def describe(problem: FieldProblem): String =
    problem match
      case FieldProblem.Blank(field)              => s"${label(field)}は空にできません"
      case FieldProblem.TooLong(field, limit)     => s"${label(field)}は $limit 文字以内にしてください"
      case FieldProblem.ForbiddenCharacter(field) => s"${label(field)}に使えない文字が含まれています"
```

```scala
  def explain(problems: List[FieldProblem]): String =
    "入力に誤りがあります。" + problems.map(p => s"\n- ${describe(p)}").mkString
```

誤りは `ZettaiError` の新しいケースに載せます。

```scala
  case InvalidInput(problems: List[FieldProblem])
```

ケースを足すと、第 7 章の `statusOf` が、ここでもコンパイルエラーで教えてくれました。入力の誤りは、400 に写します。

```scala
      case ZettaiError.InvalidInput(_)            => 400
```

HTTP の入口は、名前変更の POST を足します。誤りがあれば、すべてまとめて返します。

```scala
  def renameRoutes(execute: ToDoListCommand => IO[Either[ZettaiError, Unit]]): HttpRoutes[IO] =
```

```scala
          Validate.validateRename(user, list, newName).toEither match
            case Left(problems) => failure(ZettaiError.InvalidInput(problems.toNonEmptyList.toList))
```

### 実サーバーを通す

実際のサーバーを起動して、本物の HTTP で確かめます。誤りのある要求は 400 で、2 つの誤りがすべて本文に入り、ストアには何も書かれません。

```scala
  test("入力の誤りは 400 で、すべてまとめて返し、何も書かない") {
```

```scala
        assert(response._2.contains("新しい名前は 40 文字以内にしてください"))
        assert(response._2.contains("新しい名前に使えない文字が含まれています"))
        assertEquals(after, before)
```

正しい要求は名前を変え、旧い名前は 404、新しい名前で項目が読めます。項目の特殊文字は、エスケープされたままです。

```scala
        assert(newName._2.contains("write &amp; &lt;publish&gt;"))
```

存在しないリストの名前変更は 404 になります。

## まとめ

- リスト名の変更を、旧い名前の消去、新しい名前での作成、項目の状態ごとの引き継ぎで表した
- コンパイラは、`match` の更新漏れを 5 か所、2 段階で指摘した。文字列で `match` する読み戻しは指摘されず、往復のテストが見つけた
- `Either` の連鎖は最初の誤りで止まり、`Validated` の `mapN` はすべて集める
- `Validated` はモナドではない。独立した検証を並べるために、`flatMap` を持たない
- アプリカティブの 4 つの法則を確かめ、誤りを落とす壊れた合成が、両側が失敗する入力でしか見分けられないことを確かめた
- 入力の誤りは 400 で、すべてまとめて返し、入力の値は説明に含めない

`sbt test` は 109 本が通ります（第 10 章の 90 本に、19 本を足しました）。

つまずきとして、Cats の `Validated#ap` は、`fa.ap(ff)` の向き（値が左、関数が右）でした。法則のテストでは、`Applicative` のインスタンスを通して、`ap(ff)(fa)` の向きで書きました。

次章では、ログと JSON を扱います。ライブラリを借りるか、自作するかを先に判断します。

## この章で書いたコード

- `apps/scala/zettai/src/main/scala/zettai/Events.scala`
- `apps/scala/zettai/src/main/scala/zettai/Commands.scala`
- `apps/scala/zettai/src/main/scala/zettai/Errors.scala`
- `apps/scala/zettai/src/main/scala/zettai/Validate.scala`
- `apps/scala/zettai/src/main/scala/zettai/ZettaiRoutes.scala`
- `apps/scala/zettai/src/main/scala/zettai/PostgresStore.scala`
- `apps/scala/zettai/src/test/scala/zettai/RenameSuite.scala`
- `apps/scala/zettai/src/test/scala/zettai/ValidationSuite.scala`
- `apps/scala/zettai/src/test/scala/zettai/RenameOverHttpSuite.scala`
- `apps/scala/zettai/src/test/scala/zettai/PostgresStoreSuite.scala`

## 参照

- [Cats の Validated](https://typelevel.org/cats/datatypes/validated.html)
- [Cats の Applicative](https://typelevel.org/cats/typeclasses/applicative.html)
