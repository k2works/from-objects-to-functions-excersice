---
type: Article
title: "第 7 章 関数型手法によるエラーハンドリング"
description: "Zettai 連載 Flix 版の第 7 章。失敗の理由を ZettaiError の構造に載せ、標準の Result で成功と失敗を合成する。Result.map のファンクタ則を全パターンで確かめ、壊れた map を見分けられることを示す。型変更の波及は 4 ファイル。Outcome は作らない。"
tags: [article, zettai, flix, error-handling, functor, result, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-02T22:30:00Z }
---

# 第 7 章 関数型手法によるエラーハンドリング

`Option` の `None` は「値がない」ことだけを表し、理由を説明しません。空のイベント列も、「何も起きなかった」と「操作を拒否した」を区別できません。この章では、失敗の理由を閉じた型に載せ、標準の `Result` で成功と失敗を合成します。

Kotlin 版は第 7 章で `Outcome` を自作し、Rust 版は `Result` が言語にありました。Flix にも標準の `Result` があります。**この章の主題は、作ることではなく、使う前に何を確かめるかです。**

## より適切なエラー処理

Zettai で区別したい失敗は 4 つです。第 6 章で、すでに `ZettaiError` として定義しています。

| 失敗 | 型 | HTTP |
| :--- | :--- | :---: |
| リストが見つからない | `ListNotFound` | 404 |
| リストがすでにある | `ListAlreadyExists` | 400 |
| 項目が見つからない | `ItemNotFound` | 404 |
| 状態遷移が許されない | `InvalidTransition` | 400 |

失敗の種類を HTTP のステータスに写す関数は、全ケースを列挙する `match` で書きます。

```flix
    pub def statusOf(error: ZettaiError): Int32 =
        match error {
            case ZettaiError.ListNotFound(_, _)         => 404
            case ZettaiError.ItemNotFound(_, _, _)      => 404
            case ZettaiError.ListAlreadyExists(_, _)    => 400
            case ZettaiError.InvalidTransition(_, _, _) => 400
        }
```

失敗の種類を足したのに、この関数を直し忘れると、コンパイラが `Non-exhaustive match` で拒否します。写像の更新漏れが、実行時ではなくコンパイルで見つかります。

テストは、404 になる失敗と 400 になる失敗を、それぞれ確かめます。

```flix
    @Test
    def aRequestTheDomainRefusesIsABadRequest(): Unit \ Assert = {
        Assert.assertEq(expected = 400, Http.statusOf(ZettaiError.ListAlreadyExists(uberto(), book())));
        Assert.assertEq(expected = 400, Http.statusOf(ZettaiError.InvalidTransition("x", ToDoStatus.Todo, ToDoStatus.Blocked)))
    }
```

## ファンクタと圏を学ぶ

`Result[ZettaiError, a]` を、失敗または値を持つ文脈と考えます。成功した値だけを変換する操作が `map` です。

```text
Ok(2) |> map(x -> x + 3)    -> Ok(5)
Err(e) |> map(x -> x + 3)   -> Err(e)
```

`map` が予測可能に合成できるには、2 つの法則が必要です。

1. 恒等則: 何もしない関数で `map` しても、元の値と同じ
2. 合成則: `f` で `map` してから `g` で `map` するのと、`g(f(x))` で `map` するのは同じ

この法則を満たす `map` を持つ構造を**ファンクタ**と呼びます。名前より重要なのは、処理を分割または合成しても、結果が変わらないことです。

Flix の標準ライブラリには、この構造を表す型クラス `Functor` があります。`Result` だけでなく、`Option` や `List` も同じ `map` の形で使えます。

```flix
    @Test
    def theSameMapShapeWorksOnListOptionAndResult(): Unit \ Assert = {
        let ok: Result[ZettaiError, Int32] = Ok(1);
        Assert.assertEq(expected = 2 :: Nil, Functor.map(x -> x + 1, 1 :: Nil));
        Assert.assertEq(expected = Some(2), Functor.map(x -> x + 1, Some(1)));
        Assert.assertEq(expected = Ok(2), Functor.map(x -> x + 1, ok))
    }
```

対象は違いますが、「文脈を保ったまま、中の値を変換する」という同じ形です。圏論を先に学ばなくても、法則をテストして、リファクタリングの根拠にできます。

### 法則を全パターンで確かめる

第 5 章と同じく、乱数ではなく全パターンで確かめます。成功の値は `Int32` の端を含めて 5 つ、失敗は 4 種類すべてを並べます。

```flix
    def outcomes(): List[Result[ZettaiError, Int32]] = List#{
        Ok(Int32.minValue()), Ok(-1), Ok(0), Ok(1), Ok(Int32.maxValue()),
        Err(ZettaiError.ListNotFound(User.User("u"), ListName.ListName("l"))),
        Err(ZettaiError.ListAlreadyExists(User.User("u"), ListName.ListName("l"))),
        Err(ZettaiError.ItemNotFound(User.User("u"), ListName.ListName("l"), "x")),
        Err(ZettaiError.InvalidTransition("x", ToDoStatus.Todo, ToDoStatus.Blocked))
    }
```

関数も固定せず、4 つの関数の全組み合わせで確かめます。

```flix
    def intFunctions(): List[Int32 -> Int32] = List#{x -> x + 1, x -> x * 2, x -> 0 - x, x -> x}
```

法則は、**渡された `map` が満たすか**を返す関数として書きます。これで、標準の `map` にも、壊れた `map` にも使えます。

```flix
    def identityLaw(mapper: (Int32 -> Int32, Result[ZettaiError, Int32]) -> Result[ZettaiError, Int32]): Bool =
        List.forAll(o -> mapper(x -> x, o) == o, outcomes())
```

```flix
    def compositionLaw(mapper: (Int32 -> Int32, Result[ZettaiError, Int32]) -> Result[ZettaiError, Int32]): Bool =
        List.forAll(o ->
            List.forAll(f ->
                List.forAll(g -> mapper(g, mapper(f, o)) == mapper(x -> g(f(x)), o), intFunctions()),
                intFunctions()),
            outcomes())
```

標準の `Result.map` は、両方の法則を満たします。

```flix
    @Test
    def mapPreservesIdentity(): Unit \ Assert =
        Assert.assertTrue(identityLaw(Result.map))

    @Test
    def mapComposes(): Unit \ Assert =
        Assert.assertTrue(compositionLaw(Result.map))
```

失敗は、`map` で変換されずにそのまま返ります。

```flix
    @Test
    def aFailureIsLeftUntouchedByMap(): Unit \ Assert =
        List.forEach(o -> match o {
            case Err(e) => Assert.assertEq(expected = Err(e), Result.map(x -> x + 1, o))
            case Ok(_)  => ()
        }, outcomes())
```

### 法則のテストは、壊れた実装を見分けられるか

全パターンを走査するテストは、何も確かめていなくても通ってしまいます。そこで、成功した値に関数を 2 回当ててしまう、壊れた `map` を用意しました。

```flix
    def brokenMap(f: Int32 -> Int32, r: Result[ZettaiError, Int32]): Result[ZettaiError, Int32] =
        match r {
            case Ok(x)  => Ok(f(f(x)))
            case Err(e) => Err(e)
        }
```

この `map` は、何もしない関数で `map` すれば元に戻るので、恒等則は満たします。しかし、合成則は破れます。テストは、その違いを見分けられることを確かめます。

```flix
    @Test
    def theLawTestsTellABrokenMapApart(): Unit \ Assert = {
        Assert.assertTrue(identityLaw(brokenMap));
        Assert.assertFalse(compositionLaw(brokenMap))
    }
```

**恒等則だけを確かめていたら、この壊れた `map` を見逃します。** 法則は 2 つとも確かめる必要があります。

## ファンクタを使ったエラーハンドリング

アプリケーションの入口の `getList` の戻り値を、`Option` から `Result` に変えます。見つからなかったことが、理由つきで運ばれます。

```flix
    pub type alias ToDoListHub[ef: Eff] = {
        getList = (User, ListName) -> Result[ZettaiError, ToDoList] \ ef
    }
```

インメモリの実装は、ドメインの `Option` を `Result` に写します。

```flix
    pub def inMemory(lists: Map[User, List[ToDoList]]): ToDoListHub[{}] =
        todoListHub((user, name) -> match Domain.findToDoList(lists, user, name) {
            case Some(list) => Ok(list)
            case None       => Err(ZettaiError.ListNotFound(user, name))
        })
```

`Domain.findToDoList` は `Option` のままです。ドメインの検索は「あるか、ないか」を答えるだけで、「見つからなかったことが失敗か」は、呼び出し側の文脈が決めるからです。

HTTP 側では、`Result` の両方を応答に変えます。

```flix
    def failure(error: ZettaiError): Response =
        { status = statusOf(error), contentType = "text/plain; charset=utf-8", body = ToString.toString(error) }

    def respond(found: Result[ZettaiError, ToDoList]): Response =
        match found {
            case Ok(todoList) => html(200, Html.render(todoList))
            case Err(error)   => failure(error)
        }
```

効果ハンドラ版の `getList` も、同じ戻り値の型にしました。

```flix
        def getList(user: User, name: ListName): Result[ZettaiError, ToDoList]
```

### 型を変えた波及を測る

この変更で直したファイルを数えました。

| ファイル | 変更の内容 |
| :--- | :--- |
| `src/Hub.flix` | 型の別名と、インメモリの実装 |
| `src/Lookup.flix` | 効果の操作の戻り値と、ハンドラ |
| `src/Http.flix` | `respond` が `Result` を受け取り、ステータスの写像を追加 |
| `test/TestHub.flix` | 期待値を `Ok` と `Err` に |

**4 ファイルです。** Kotlin FP 版は 5 ファイルでした。

変わらなかったものも重要です。ドメイン、イベント、遷移表、コマンド、イベントストアのコード、そして `TestHttpApp`、`TestJdkServer`、`TestEffectDi` は、1 文字も変えていません。第 4 章の `bothStylesGiveTheSameResponse` は、関数値のレコード版と効果ハンドラ版が同じレスポンスを返すことを確かめるテストで、両方を同時に変えても緑のままでした。

この数字は、型を変える前に予想できたものではなく、変えたあとに数えたものです。ただし、「小さい波及」が良い設計の結果なのか、単にこのコードベースが小さいからなのかは、この数字だけでは分かりません。

## `Result` を使って実装する

Kotlin 版は、この章で `Outcome` を自作しました。Flix では、作らない判断をしました。判断の記録は [ADR-047](../../adr/ADR-047-flix-use-standard-result.md) にあります。

作らない理由は、必要な操作が標準にそろっているからです。第 6 章の `handle` は、すでに `forM` で `Result` の連鎖を書いています。この章で `map` の法則を確かめ、`Functor` の型クラスのインスタンスであることも確かめました。

**使う判断で確かめたことを、ADR に残しました。**

- `Result.map` が、端の値と 4 種類の失敗を含む全パターンで、2 つの法則を満たす
- 法則のテストが、壊れた `map` を見分けられる
- 型を変えたときの波及が、4 ファイルだった

一方で、確かめていないことも書いてあります。標準の `Result` の、`map` と `forM` 以外の操作です。それらを使うときに、その時点で確かめます。

自作するのは、失敗側の型 `ZettaiError` だけです。標準の `Result` は成功と失敗の入れ物であって、業務上ありうる失敗の集合までは与えてくれません。

## まとめ

- 失敗の理由を `ZettaiError` の構造に載せ、HTTP のステータスへの写像を全ケースの `match` にしました。
- `Result.map` の恒等則と合成則を、端の値と 4 種類の失敗を含む全パターンで確かめました。
- 恒等則だけを満たす壊れた `map` を用意し、法則のテストがそれを見分けることを確かめました。
- `getList` の戻り値を `Result` に変えたとき、直したのは 4 ファイルでした。
- `Outcome` は作らず、標準の `Result` を使いました。使う前に確かめたことを ADR に残しました。
- `flix test` は 66 本が通ります。

次章では、イベントを読み取り専用のビューに射影します。`map` が、イベントのリストから表示用の値を作る道具になります。

## この章で書いたコード

- ステータスへの写像: `apps/flix/zettai/src/Http.flix`
- Hub の型: `apps/flix/zettai/src/Hub.flix`
- 効果としての依存: `apps/flix/zettai/src/Lookup.flix`
- テスト: `apps/flix/zettai/test/TestErrorMapping.flix`、`TestResultFunctor.flix`、`TestHub.flix`

## 参照

- Uberto Barbini『From Objects to Functions』第 7 章
- [ADR-047 Flix 版は標準の Result を使い、Outcome を作らない](../../adr/ADR-047-flix-use-standard-result.md)
- [Flix 公式ドキュメント](https://doc.flix.dev/)
