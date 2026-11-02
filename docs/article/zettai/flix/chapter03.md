---
type: Article
title: "第 3 章 ドメインの定義とテスト"
description: "Zettai 連載 Flix 版の第 3 章。受け入れテストを業務の言葉の関数として書き、関数値のレコードで domain と HTTP の 2 経路に差し替える。Cucumber を採らなかった判断と、純粋な関数を IO の枠に入れるときの明示的な宣言を記録する。"
tags: [article, zettai, flix, acceptance-test, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-02T16:30:00Z }
---

# 第 3 章 ドメインの定義とテスト

第 2 章のテストは HTTP の縦串をよく確かめますが、URL、ステータス、HTML まで知っています。この章では「ユーザーが自分の ToDo リストを見られる」という業務の言葉を中心に戻します。

## 受け入れテストを改善する

業務の期待を、経路を知らない関数として書きます。Gherkin の「前提・もし・ならば」に相当するものを、関数の名前と本体で表します。

```flix
    pub def seeTheItemsOfAList(actions: ZettaiActions): Unit \ {Assert, IO} = {
        let found = actions#getToDoList(User.User("uberto"), ListName.ListName("book"));
        check(actions, Some(List#{
            ToDoItem.ToDoItem("write chapter"),
            ToDoItem.ToDoItem("publish book"),
            ToDoItem.ToDoItem("read <today> & \"FP\"")
        }), found)
    }
```

この関数には URL も、ステータスも、HTML も出てきません。あるのは「ユーザー `uberto` のリスト `book` を見る」と「項目は次のとおり」だけです。インフラの変更と業務仕様の変更を、これで区別できます。項目には `<` や `&` を含むものを入れてあります。エスケープの往復も、ここで一緒に確かめます。

存在しないリストのシナリオも同じ形です。

```flix
    pub def aMissingListIsNotFound(actions: ZettaiActions): Unit \ {Assert, IO} = {
        let found = actions#getToDoList(User.User("uberto"), ListName.ListName("missing"));
        check(actions, None, found)
    }
```

## 高階関数を使う

シナリオが使う操作を、ドメインの型だけで定義します。

```flix
    pub type alias ZettaiActions = {
        name = String,
        getToDoList = (User, ListName) -> Option[List[ToDoItem]] \ IO,
        close = Unit -> Unit \ IO
    }
```

`ZettaiActions` は関数値を持つレコードです。継承ではなく、値の差し替えで経路を組み立てます。プロダクションコードの DI コンテナではなく、テストのための契約です。

`getToDoList` の戻り値は `Option[List[ToDoItem]]` で、`String` や HTML ではありません。シナリオはドメインの値だけを扱います。

## ドメインとインフラストラクチャの分離

2 つの経路は、同じ型の別実装です。

domain 経路は、HTTP を通らずに functional core の検索関数を直接呼びます。

```flix
    pub def domainActions(lists: Map[User, List[ToDoList]]): ZettaiActions = {
        name = "domain",
        getToDoList = (user, listName) ->
            inIO(Domain.findToDoList(lists, user, listName)
                |> Option.map(l -> match l { case ToDoList.ToDoList(_, items) => items })),
        close = () -> inIO(())
    }
```

HTTP 経路は、実際のサーバーを起動してパスを組み立て、返ってきた HTML から項目を読み戻します。

```flix
    pub def httpActions(lists: Map[User, List[ToDoList]]): ZettaiActions \ IO = {
        let server = JdkServer.start(0, request ->
            Http.route((user, listName) -> Domain.findToDoList(lists, user, listName), request));
        let port = JdkServer.port(server);
        {
            name = "http",
            getToDoList = (user, listName) -> {
                let User.User(u) = user;
                let ListName.ListName(l) = listName;
                let (status, body) = HttpTestClient.get(port, "/todo/${u}/${l}");
                if (status == 200) Some(HtmlReader.items(body)) else None
            },
            close = () -> JdkServer.stop(server)
        }
    }
```

domain 経路だけが失敗すれば、ドメインの振る舞いに問題があります。HTTP 経路だけが失敗すれば、パス変換、ステータス、HTML のどこかに問題があります。両方を同じ期待で実行することで、アダプタが業務判断を追加していないことを検証できます。

### 純粋な関数を `IO` の枠に入れる

`domainActions` に `inIO` が出てきました。第 2 章で見たとおり、Flix は純粋な関数を、`IO` を期待する場所に渡すことを許しません。レコードの `getToDoList` は `\ IO` と宣言しているので、HTTP 経路は自然に収まりますが、domain 経路は純粋なままでは収まりません。

そこで、枠に入れる操作を名前の付いた補助関数にしました。

```flix
    def inIO(value: a): a \ IO = unchecked_cast(value as _ \ IO)
```

`unchecked_cast` は「コンパイラは確かめられないが、私が保証する」という宣言です。ここでは、純粋な値に `IO` の効果を**足す**だけなので、安全です。逆に、`IO` を持つ関数を純粋に見せかける向きは、嘘になるので使いません。使う場所がテストの 1 箇所に限られるよう、`Routes` の中に閉じ込めました。

### 同じシナリオをすべての経路で実行する

反復は `Routes.onEveryRoute` に閉じ込めます。

```flix
    pub def onEveryRoute(lists: Map[User, List[ToDoList]], scenario: ZettaiActions -> Unit \ {Assert, IO}): Unit \ {Assert, IO} = {
        let routes = domainActions(lists) :: httpActions(lists) :: Nil;
        List.forEach(actions -> {
            scenario(actions);
            actions#close()
        }, routes)
    }
```

テストは、データとシナリオを渡すだけです。

```flix
    @Test
    def seeTheItemsOfAList(): Unit \ {Assert, IO} =
        Routes.onEveryRoute(lists(), Scenarios.seeTheItemsOfAList)
```

プロダクションコードにも、シナリオにも、経路の名前は入りません。テストの実行方法を、ドメイン設計と業務仕様へ漏らさないためです。

## ドメインからテストを駆動する

HTTP 経路は、HTML という外部表現を扱います。読み戻す処理は、テスト側の小さな関数に閉じ込めました。

```flix
    pub def items(html: String): List[ToDoItem] =
        match String.splitOn(substr = "<li>", html) {
            case _ :: fragments => List.filterMap(itemOf, fragments)
            case Nil            => Nil
        }
```

この関数自体にもテストがあります。`Html.render` が出力した HTML を `HtmlReader.items` が読み戻すと、元の項目に戻ることを確かめています。

```flix
    @Test
    def roundTripsWhatHtmlRenders(): Unit \ Assert = {
        let items = List#{ToDoItem.ToDoItem("a & b"), ToDoItem.ToDoItem("<i>c</i>")};
        let list = Domain.ToDoList.ToDoList(Domain.ListName.ListName("x"), items);
        Assert.assertEq(expected = items, HtmlReader.items(Html.render(list)))
    }
```

以後の検証は、HTML の文字列ではなく `ToDoItem` のリスト同士で行います。

### 経路が失敗を絞り込む

実際に HTTP 経路だけを壊して確かめました。`Http.route` のパスの照合を `"todo"` から `"todos"` に変えると、受け入れテストは次のように落ちます。domain 経路は通ったままです。

```bash
TestAcceptance.seeTheItemsOfAList   FAIL  (Assertion failed: expected `(http, Some(ToDoItem(write chapter) :: ToDoItem(publish book) :: ToDoItem(read <today> & "FP") :: Nil))`, but got `(http, None)`)
```

失敗メッセージの先頭に `http` と出ています。シナリオ側で、経路の名前と結果を組にして比べているからです。

```flix
    def check(actions: ZettaiActions, expected: Option[List[ToDoItem]], actual: Option[List[ToDoItem]]): Unit \ Assert =
        Assert.assertEq(expected = (actions#name, expected), (actions#name, actual))
```

`Assert.assertEq` はメッセージを付けられないので、組にして比べる方法を採りました。

なお、この壊し方で落ちたのは受け入れテストだけではありません。第 2 章の `TestHttpApp.returnsTheListAsHtml` と `TestJdkServer.servesTheListOverRealHttp` も落ちました。受け入れテストが他のテストの代わりをするのではなく、重なりを持って守っています。

一方で、`Html.render` から `&` のエスケープを外してみたときは、落ちたのはエスケープの単体テスト 1 本だけで、受け入れテストは通りました。`HtmlReader` の復元が対称に作ってあるので、往復すると元に戻ってしまうためです。**受け入れテストは、単体テストの代わりにはなりません。**

## DDT を関数値のレコードに変換する

既存の Kotlin 版は DDT を Pesticide で実行し、Kotlin FP 版は Gherkin と Cucumber を使いました。Flix 版は、どちらも使いません。関数値のレコードで経路を差し替えます。

判断の記録は [ADR-046](../../adr/ADR-046-flix-acceptance-by-record-of-functions.md) にあります。要点は次のとおりです。

- 必要なのは経路の差し替えで、Gherkin ではない
- Flix の標準の道具で足りる
- **Cucumber が使えないと確かめたわけではない。** Flix でアノテーション付きのクラスを定義する手段が、公式ドキュメントに見つからなかったので、試さずに採らなかった

試していないことは、試していないと書いておきます。

## まとめ

- 受け入れテストを、URL もステータスも出てこない業務の言葉の関数として書きました。
- `ZettaiActions` を関数値のレコードにし、domain と HTTP の 2 経路を差し替えました。
- 同じシナリオを両方の経路で実行し、HTTP 経路だけを壊すと失敗メッセージに `(http, None)` と経路名が出ることを確かめました。
- 純粋な関数を `IO` の枠に入れる操作を、`inIO` という名前の付いた 1 箇所に閉じ込めました。
- Cucumber は、試さずに採りませんでした。その事実を ADR に残しました。
- `flix test` は 21 本が通ります。

つまずきとして、レコードの一部のフィールドが純粋なままだと、効果の不一致 `Unable to unify the effect formulas: 'IO' and 'Pure'.` がレコード全体の位置で報告されます。最初は `getToDoList` だけを直しましたが、`close = () -> ()` も純粋なままだったため、エラーは消えませんでした。どのフィールドが原因かはエラーから読み取りにくいので、枠に入れる関数 `inIO` を先に作ってから、すべてのフィールドに使うのが近道でした。

次章では、取得関数を渡すだけだった依存関係を発展させ、ドメインとアダプタのモデリングを進めます。第 2 章の `\ ef` と、この章の `inIO` が、関数型 DI にどうつながるかを見ます。

## この章で書いたコード

- 契約: `apps/flix/zettai/test/ZettaiActions.flix`
- シナリオ: `apps/flix/zettai/test/Scenarios.flix`
- 経路: `apps/flix/zettai/test/Routes.flix`
- HTML の読み戻し: `apps/flix/zettai/test/HtmlReader.flix`
- テスト: `apps/flix/zettai/test/TestAcceptance.flix`、`TestHtmlReader.flix`

## 参照

- Uberto Barbini『From Objects to Functions』第 3 章
- [ADR-046 Flix 版は受け入れテストの経路を関数値のレコードで差し替え、Cucumber を入れない](../../adr/ADR-046-flix-acceptance-by-record-of-functions.md)
