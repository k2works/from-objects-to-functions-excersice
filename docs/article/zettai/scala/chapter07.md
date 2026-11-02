---
type: Article
title: "第 7 章 関数型手法によるエラーハンドリング"
description: "Zettai 連載 Scala 版の第 7 章。業務の失敗を標準の Either で運び、IO の失敗と使い分ける。Either の map がファンクタの法則を満たすことを確かめ、型を変えたときの波及を測る。"
tags: [article, zettai, scala, cats-effect, error-handling, functor, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-02T11:00:00Z }
---

# 第 7 章 関数型手法によるエラーハンドリング

この章では、失敗を値として運びます。Scala と Cats Effect では、運び方に 2 つの選択肢があります。**`Either` で運ぶか、`IO` の失敗（例外）で運ぶか**です。両方を書いて並べ、使う方を決めます。

## より適切なエラー処理

Zettai で区別したい失敗は 4 つです。第 6 章で、すでに `ZettaiError` として定義しています。

| 失敗 | 型 | HTTP |
| :--- | :--- | :---: |
| リストが見つからない | `ListNotFound` | 404 |
| リストがすでにある | `ListAlreadyExists` | 400 |
| 項目が見つからない | `ItemNotFound` | 404 |
| 状態遷移が許されない | `InvalidTransition` | 400 |

失敗の種類を HTTP のステータスに写す関数は、全ケースを列挙する `match` で書きます。

```scala
  def statusOf(error: ZettaiError): Int =
    error match
      case ZettaiError.ListNotFound(_, _)         => 404
      case ZettaiError.ItemNotFound(_, _, _)      => 404
      case ZettaiError.ListAlreadyExists(_, _)    => 400
      case ZettaiError.InvalidTransition(_, _, _) => 400
```

失敗の種類を足したのに、この関数を直し忘れると、コンパイラが `-Werror` の下で止めます。写像の更新漏れが、実行時ではなくコンパイルで見つかります。

テストは、404 になる失敗と 400 になる失敗を、それぞれ確かめます。

```scala
  test("ドメインが拒否した要求は 400") {
    assertEquals(ZettaiRoutes.statusOf(ZettaiError.ListAlreadyExists(uberto, book)), 400)
    assertEquals(
      ZettaiRoutes.statusOf(ZettaiError.InvalidTransition("x", ToDoStatus.Todo, ToDoStatus.Blocked)),
      400
    )
  }
```

## ファンクタと圏を学ぶ

`Either[ZettaiError, A]` を、失敗または値を持つ文脈と考えます。成功した値だけを変換する操作が `map` です。

```text
Right(2).map(x => x + 3)   -> Right(5)
Left(e).map(x => x + 3)    -> Left(e)
```

`map` が予測可能に合成できるには、2 つの法則が必要です。

- 恒等則: `map(identity)` は何も変えない
- 合成則: `map(f).map(g)` は `map(f andThen g)` と同じ

### 法則を全パターンで確かめる

確かめる値は、成功の端の値（`Int` の最小値と最大値）と、4 種類の失敗を含む 8 つです。

```scala
  private val samples: List[Result[Int]] =
    List(Right(0), Right(1), Right(Int.MinValue), Right(Int.MaxValue)) ++ List[Result[Int]](
      Left(ZettaiError.ListNotFound(uberto, book)),
      Left(ZettaiError.ListAlreadyExists(uberto, book)),
      Left(ZettaiError.ItemNotFound(uberto, book, "x")),
      Left(ZettaiError.InvalidTransition("x", ToDoStatus.Todo, ToDoStatus.Blocked))
    )
```

```scala
  test("恒等則: map(identity) は何も変えない") {
    for sample <- samples do assertEquals(Functor[Result].map(sample)(identity), sample)
  }
```

```scala
  test("合成則: map(f).map(g) は map(g compose f) と同じ") {
    for sample <- samples do
      assertEquals(
        Functor[Result].map(Functor[Result].map(sample)(f))(g),
        Functor[Result].map(sample)(f andThen g)
      )
  }
```

`Functor[Result]` は Cats が `Either` に与えるファンクタのインスタンスです。**標準の `Either` と Cats の型クラスは、最初からつながっています。**

### 法則のテストは、壊れた実装を見分けられるか

法則のテストが、本当に壊れた `map` を見分けられるのかを確かめます。成功値に関数を 2 回当てる、壊れた `map` を作ります。

```scala
  private def brokenMap[A](result: Result[A])(h: A => A): Result[A] = result.map(h).map(h)
```

```scala
  test("壊れた map は、恒等則を通るが、合成則で落ちる") {
    assert(samples.forall(s => brokenMap(s)(identity) == s))
    assert(samples.exists(s => brokenMap(brokenMap(s)(f))(g) != brokenMap(s)(f andThen g)))
  }
```

恒等関数を 2 回当てても何も変わらないので、恒等則は通ります。合成則は落ちます。**1 つの法則だけでは、壊れた実装を見逃すことがあります。** 2 つとも確かめる理由です。

## ファンクタを使ったエラーハンドリング

アプリケーションの入口の `getList` の戻り値を、`Option` から `Either` に変えます。見つからなかったことが、理由つきで運ばれます。

```scala
final case class Hub(getList: (User, ListName) => IO[Either[ZettaiError, ToDoList]])
```

インメモリの実装は、ドメインの `Option` を `Either` に写します。

```scala
    Hub((user, name) =>
      IO.pure(Domain.findToDoList(lists, user, name).toRight(ZettaiError.ListNotFound(user, name)))
    )
```

`Domain.findToDoList` は `Option` のままです。ドメインの検索は「あるか、ないか」を答えるだけで、「見つからなかったことが失敗か」は、呼び出し側の文脈が決めるからです。

HTTP 側では、`Either` の両方を応答に変えます。

<!-- code-check: ignore この章を書いた時点のコード（コミット 785548f）。第 9 章で想定外の失敗を 500 にする処理を足した -->
```scala
        hub.getList(User(user), ListName(list)).flatMap {
          case Right(found) => Ok(Html.render(found)).map(_.withContentType(html))
          case Left(error)  => failure(error)
        }
```

<!-- code-check: ignore この章を書いた時点のコード（コミット 785548f）。第 11 章で InvalidInput の本文を、入力の誤りの説明にした -->
```scala
  private def failure(error: ZettaiError): IO[Response[IO]] =
    IO.pure(Response[IO](Status.fromInt(statusOf(error)).getOrElse(Status.InternalServerError)).withEntity(error.toString))
```

存在しないリストは、404 で失敗の理由を返します。

```scala
  test("存在しないリストは 404 で、失敗の理由を返す") {
    for
      response <- get("/todo/uberto/missing")
      body     <- response.as[String]
    yield
      assertEquals(response.status, Status.NotFound)
      assert(body.contains("ListNotFound"))
  }
```

応答の本文は、今は `ZettaiError` の文字列表現をそのまま返します。入っているのは利用者名とリスト名だけなので、漏れて困るものはありません。第 9 章で保存の失敗が加わるとき、本文に出す内容を見直します。

### 型を変えた波及を測る

この変更で直したファイルを数えました。

| ファイル | 変更の内容 |
| :--- | :--- |
| `src/main/scala/zettai/Hub.scala` | 型と、インメモリの実装 |
| `src/main/scala/zettai/View.scala` | `Either` から `Option` に戻す（`toOption`） |
| `src/main/scala/zettai/ZettaiRoutes.scala` | `Either` を受け取り、ステータスの写像を追加 |

**本番コードは 3 ファイルです。** Kotlin FP 版は 5 ファイル、Flix 版は 4 ファイルでした。

変わらなかったものも重要です。ドメイン、イベント、遷移表、コマンド、イベントストア、サーバーのコードと、既存のテストは 1 文字も変えていません。ただし `ZettaiRoutesSuite` には、失敗の理由を確かめる検査を足しました。型の変更で強いられたのではなく、足したくて足した変更です。

この数字は、型を変える前に予想できたものではなく、変えたあとに数えたものです。ただし、「小さい波及」が良い設計の結果なのか、単にこのコードベースが小さいからなのかは、この数字だけでは分かりません。

## `Either` を使って実装する

Kotlin 版は、この章で `Outcome` を自作しました。Scala では、作らない判断をしました。判断の記録は [ADR-050](../../../adr/ADR-050-scala-either-for-domain-failures.md) にあります。

作らない理由は、必要な操作が標準にそろっているからです。第 6 章の `handle` は、すでに `for` で `Either` の連鎖を書いています。この章で `map` の法則を確かめ、`Functor` の型クラスのインスタンスであることも確かめました。

もう 1 つの判断が、`IO` の失敗（例外）との使い分けです。業務の失敗を `IO.raiseError` で運ぶ版を書いて並べました。

```scala
  private def raising(user: User, name: ListName): IO[ToDoList] =
    hub.getList(user, name).flatMap(found => IO.fromEither(found.left.map(ZettaiException(_))))
```

2 つの運び方で、`attempt`（`IO` の失敗を値にして取り出す操作）の結果が違います。

```scala
  test("Either で運ぶと、業務の失敗は IO の成功の中に値として残る") {
    hub.getList(User("uberto"), ListName("book")).attempt.map { outcome =>
      assertEquals(outcome, Right(Left(missing)))
    }
  }
```

```scala
  test("IO の失敗で運ぶと、業務の失敗は例外になり、他の失敗と区別がつかない") {
    raising(User("uberto"), ListName("book")).attempt.map { outcome =>
      assertEquals(outcome.left.map(_.getMessage), Left(missing.toString))
    }
  }
```

`Either` で運ぶと、業務の失敗は `IO` の成功の中に残ります。例外で運ぶと、`Throwable` の一種になり、受け取る側が型を戻さなければなりません。

```scala
  test("例外で運ぶ版でも、受け取る側が型を戻せば、同じ失敗を取り出せる") {
    raising(User("uberto"), ListName("book")).attempt.map { outcome =>
      assertEquals(
        outcome.left.toOption.collect { case ZettaiException(error) => error },
        Some(missing)
      )
    }
  }
```

**どちらでも同じ失敗を取り出せます。** 違うのは、取り出すために受け取る側が何をしなければならないかです。`Either` なら型が教え、例外なら受け取る側が覚えていなければなりません。

**使う判断で確かめたことを、ADR に残しました。**

- `Either.map` が、端の値と 4 種類の失敗を含む全パターンで、2 つの法則を満たす
- 法則のテストが、壊れた `map` を見分けられる
- 型を変えたときの波及が、本番コード 3 ファイルだった
- `Either` と `IO` の失敗で、`attempt` の結果が違う

一方で、確かめていないことも書いてあります。`EitherT` で連鎖を短く書くこと、`Either` の `traverse` などの操作です。それらを使うときに、その時点で確かめます。

自作するのは、失敗側の型 `ZettaiError` だけです。標準の `Either` は成功と失敗の入れ物であって、業務上ありうる失敗の集合までは与えてくれません。

## まとめ

- 業務の失敗は標準の `Either` で運び、`IO` の失敗は想定外の失敗に限る（ADR-050）
- 失敗の種類を HTTP のステータスに写す `match` は、足し忘れをコンパイラが止める
- `Either` の `map` は、端の値と 4 種類の失敗の全パターンで、恒等則と合成則を満たす
- 法則のテストは、壊れた `map` を見分けられる。恒等則だけでは見逃す
- `Hub.getList` の戻り値を `Either` に変えた波及は、本番コード 3 ファイルだった
- `Either` で運ぶと型が失敗の種類を教え、例外で運ぶと受け取る側が型を戻す

`sbt test` は 53 本が通ります（第 6 章の 43 本に、10 本を足しました）。

つまずきとして、`for` の中でタプルのパターンを使うと `withFilter` が無いというコンパイルエラーになりました（第 6 章のテスト）。`flatMap` と `case` に書き換えました。

次章では、ファンクタを使ってイベントから読み取り専用の射影を作ります。

## この章で書いたコード

- `apps/scala/zettai/src/main/scala/zettai/application/Hub.scala`
- `apps/scala/zettai/src/main/scala/zettai/application/View.scala`
- `apps/scala/zettai/src/main/scala/zettai/web/ZettaiRoutes.scala`
- `apps/scala/zettai/src/test/scala/zettai/web/ErrorMappingSuite.scala`
- `apps/scala/zettai/src/test/scala/zettai/domain/errors/FunctorLawsSuite.scala`
- `apps/scala/zettai/src/test/scala/zettai/application/FailureStylesSuite.scala`
- `apps/scala/zettai/src/test/scala/zettai/web/ZettaiRoutesSuite.scala`

## 参照

- [ADR-050 業務の失敗を Either で運ぶ](../../../adr/ADR-050-scala-either-for-domain-failures.md)
- [Cats の Functor](https://typelevel.org/cats/typeclasses/functor.html)
