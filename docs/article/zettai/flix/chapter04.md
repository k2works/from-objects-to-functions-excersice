---
type: Article
title: "第 4 章 ドメインとアダプタのモデリング"
description: "Zettai 連載 Flix 版の第 4 章。依存を関数値のレコード（Hub）で渡す方法と、効果ハンドラで差し替える方法を同じ route で並べて比べ、ドメインに有限状態 ToDoStatus を足す。"
tags: [article, zettai, flix, dependency-injection, effect-handler, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-02T18:00:00Z }
---

# 第 4 章 ドメインとアダプタのモデリング

第 3 章では、同じシナリオを domain と HTTP の 2 経路で実行しました。ただし、`route` が外部の取得方法を受け取る形は、まだ関数を 1 つ渡すだけでした。この章では、ドメインの操作とアダプタの配線を分けます。

後の章で書き換わったコードのブロックには、この章を書いた時点のコミットを示してあります。

Flix 版には、他の対象に無い問いがあります。**依存を「引数」で渡すか、「効果」として型に現すか**です。同じ `route` を両方の書き方で作り、並べて比べます。

## ToDo リストを変更するための新しいストーリーを始める

これから ToDo リストの作成や項目追加へ進みます。その前に、読み取り側の依存関係を整理します。HTTP のルーティングがデータの置き場所を知っていると、変更操作を加えるたびに HTTP と保存の都合が絡みます。

必要な契約は「利用者とリスト名を受け取り、見つかればリストを返す」だけです。第 2 章の `route` は、これを関数型の引数として受け取っていました。

## 関数型の「依存性の注入」を使う

アプリケーションが公開する操作を、関数値のフィールドを持つレコードで表します。

<!-- code-check: ignore この章を書いた時点のコード（コミット 2d25366）。第 7 章で getList の戻り値を Result に変えた -->
```flix
    pub type alias ToDoListHub[ef: Eff] = {
        getList = (User, ListName) -> Option[ToDoList] \ ef
    }
```

`[ef: Eff]` は、型の別名が**効果を引数に取る**ことを表します。`getList` の効果は `Hub` を使う側が決めます。インメモリの実装なら効果なし（`{}`）、データベースなら `IO` です。

Hub を組み立てるのは、関数を受け取る関数です。

<!-- code-check: ignore この章を書いた時点のコード（コミット 2d25366）。第 7 章で getList の戻り値を Result に変えた -->
```flix
    pub def todoListHub(fetch: (User, ListName) -> Option[ToDoList] \ ef): ToDoListHub[ef] =
        { getList = fetch }
```

インメモリの実装は、同じ型を効果なしで返します。

<!-- code-check: ignore この章を書いた時点のコード（コミット 2d25366）。第 7 章で getList の戻り値を Result に変えた -->
```flix
    pub def inMemory(lists: Map[User, List[ToDoList]]): ToDoListHub[{}] =
        todoListHub((user, name) -> Domain.findToDoList(lists, user, name))
```

テストでは、ラムダをそのまま渡せます。

<!-- code-check: ignore この章を書いた時点のコード（コミット 2d25366）。第 7 章で getList の戻り値を Result に変えた -->
```flix
    @Test
    def aHubIsBuiltFromAnyFunctionOfTheRightType(): Unit \ Assert = {
        let hub = Application.Hub.todoListHub((user, name) ->
            if (user == User.User("uberto") and name == ListName.ListName("book")) Some(book()) else None);
        Assert.assertEq(expected = Some(book()), hub#getList(User.User("uberto"), ListName.ListName("book")));
        Assert.assertEq(expected = None, hub#getList(User.User("alice"), ListName.ListName("book")))
    }
```

`route` は Hub を受け取ります。取得関数ではなく、Hub の `getList` を呼びます。

```flix
    pub def route(hub: ToDoListHub[ef], request: Request): Response \ ef =
        if (request#method != "GET")
            methodNotAllowed()
        else
            match parse(request#path) {
                case Some((user, list)) => respond(hub#getList(user, list))
                case None               => notFound()
            }
```

パスの解釈と、リストの有無からレスポンスへの変換は、`parse` と `respond` に切り出しました。次に書く効果版と共有するためです。

### 依存を効果として型に現す

Flix には、もう 1 つの方法があります。**効果そのものを依存として宣言する**方法です。

<!-- code-check: ignore この章を書いた時点のコード（コミット 2d25366）。第 7 章で getList の戻り値を Result に変えた -->
```flix
    pub eff ToDoLists {
        def getList(user: User, name: ListName): Option[ToDoList]
    }
```

`eff` は「こういう操作ができる」という宣言だけで、実装を持ちません。実装は、呼び出し側が**ハンドラ**で与えます。

<!-- code-check: ignore この章を書いた時点のコード（コミット 2d25366）。第 7 章で getList の戻り値を Result に変えた -->
```flix
    pub def handleInMemory(lists: Map[User, List[ToDoList]], f: Unit -> a \ ef): a \ (ef - ToDoLists) =
        run {
            f()
        } with handler ToDoLists {
            def getList(user, name, resume) = resume(Domain.findToDoList(lists, user, name))
        }
```

戻り値の効果 `ef - ToDoLists` は、「`f` の効果から `ToDoLists` を取り除いたもの」です。ハンドラが依存を満たしたので、その効果は型から消えます。

この版の `route` は、Hub を受け取りません。依存は型に現れます。

```flix
    pub def routeWithEffect(request: Request): Response \ Application.Lookup.ToDoLists =
        if (request#method != "GET")
            methodNotAllowed()
        else
            match parse(request#path) {
                case Some((user, list)) => respond(Application.Lookup.ToDoLists.getList(user, list))
                case None               => notFound()
            }
```

シグネチャの `\ Application.Lookup.ToDoLists` が、「この関数は `ToDoLists` の実装を必要とする」と宣言しています。ハンドラ無しで呼ぶと、コンパイルが通りません。

```bash
-- Type Error [E6216] -------------------------------------------- test/Tmp.flix

>> Unexpected effect 'ToDoLists' in function declared as {'Assert'}.

3 |     def noHandler(): Unit \ Assert = {
                                ^^^^^^
                                function declared as {'Assert'}

4 |         let r = Http.routeWithEffect({ method = "GET", path = "/todo/uberto/book" });
                    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                    'ToDoLists' used here
```

依存の差し忘れが、実行時ではなく型検査で見つかります。DI コンテナの「実行したら見つからない」とは逆です。

### 2 つの書き方は同じ結果を返す

両方を同じ入力で動かし、レスポンスが一致することを確かめます。

```flix
    /// 関数値のレコードで依存を渡す版
    def byHub(path: String): Response =
        Web.Http.route(Application.Hub.inMemory(lists()), get(path))

    /// 効果ハンドラで依存を差し替える版
    def byHandler(path: String): Response =
        Application.Lookup.handleInMemory(lists(), () -> Web.Http.routeWithEffect(get(path)))
```

```flix
    @Test
    def bothStylesGiveTheSameResponse(): Unit \ Assert = {
        let paths = List#{"/todo/uberto/book", "/todo/uberto/missing", "/todo/nobody/book", "/other", "/todo/uberto"};
        List.forEach(path -> {
            let a = byHub(path);
            let b = byHandler(path);
            Assert.assertEq(expected = (a#status, a#contentType, a#body), (b#status, b#contentType, b#body))
        }, paths)
    }
```

レスポンスのレコードには `Eq` が無いので、フィールドを組にして比べています。

### どちらを採るか

| 観点 | 関数値のレコード（Hub） | 効果ハンドラ |
| :--- | :--- | :--- |
| 依存の見え方 | 引数に現れる | 戻り値の効果に現れる |
| 差し忘れ | 引数が足りずコンパイルエラー | ハンドラが無くコンパイルエラー |
| 他の対象との共通性 | Kotlin FP 版、Rust 版と同じ形 | Flix 固有 |
| テストでの差し替え | ラムダを渡す | ハンドラで包む |
| 複数の依存 | レコードにフィールドを足す | 効果に操作を足す |

**この連載では、Hub を採ります。** 他の対象と同じ形で比較できることと、第 2 章の `\ ef` の続きとして読めることが理由です。効果ハンドラ版は、比較のために残しました。第 10 章で文脈を持ち回るときに、改めて効果ハンドラを検討します。

## 関数型コードをデバックする

関数値を渡す設計では、呼び出し先のクラス名から処理を追う方法が使えません。代わりに、関数の入力と出力を観測します。

<!-- code-check: ignore この章を書いた時点のコード（コミット 2d25366）。第 7 章で getList の戻り値を Result に変えた -->
```flix
    @Test
    def theInMemoryHubFindsTheListOfTheUser(): Unit \ Assert = {
        let hub = Application.Hub.inMemory(Map#{User.User("uberto") => List#{book()}});
        Assert.assertEq(expected = Some(book()), hub#getList(User.User("uberto"), ListName.ListName("book")));
        Assert.assertEq(expected = None, hub#getList(User.User("uberto"), ListName.ListName("missing")))
    }
```

モックの呼び出し履歴を読む必要はありません。問題を三つの境界に分けられます。

1. `todoListHub` へ意図した関数を渡したか
2. `route` が受け取った利用者とリスト名を、そのまま `getList` に渡したか
3. `respond` が結果を正しいステータスと HTML に変換したか

それぞれを値の比較で確かめると、失敗の場所が狭くなります。

Flix では、もう 1 つ助けがあります。**効果が型に現れる**ので、「この関数は副作用を持つか」を、実行せずに読めます。`ToDoListHub[{}]` と書かれた Hub は、`getList` が何を渡されても外部に触れません。

なお、実行中の値を覗くための `dbg` に当たるものを探しましたが、Flix 0.79.0 では `dbg`、`debug`、`Debug.dbg`、`Debug.debug` のどれも見つかりませんでした。この版では、値の比較で確かめる方針を取りました。

## 関数型ドメインモデリング

依存関係を整理したので、これから変更する値も太くします。ToDo 項目には状態があります。

```flix
    pub enum ToDoStatus with Eq, ToString {
        case Todo, InProgress, Done, Blocked
    }
```

状態を `String` にすると、`"done"`、`"Done"`、`"DONE"` が別の値になります。`enum` なら、許した有限個の状態以外は作れません。`match` で全ケースを書かなければ、コンパイラが `Non-exhaustive match: missing case 'InProgress'.` と報告して拒否します。

項目は、説明と状態を持ちます。

```flix
    pub enum ToDoItem(String, ToDoStatus) with Eq, ToString
```

Flix には引数の既定値がありません。これまで `ToDoItem("write chapter")` と書いていた箇所を、状態が `Todo` の項目を作る関数にまとめました。

```flix
    pub def todo(description: String): ToDoItem = ToDoItem.ToDoItem(description, ToDoStatus.Todo)
```

新しい状態が追加されたときのために、すべての状態を返す関数も置きました。

```flix
    pub def allStatuses(): List[ToDoStatus] =
        List#{ToDoStatus.Todo, ToDoStatus.InProgress, ToDoStatus.Done, ToDoStatus.Blocked}
```

テストは、新しい項目が `Todo` から始まることを確かめます。

```flix
    @Test
    def aNewItemStartsAsTodo(): Unit \ Assert =
        Assert.assertEq(
            expected = ToDoItem.ToDoItem("write chapter", ToDoStatus.Todo),
            Domain.todo("write chapter")
        )
```

状態遷移の規則はまだ実装しません。第 6 章でコマンドを導入するまで、型に場所を作るだけに留めます。章の説明より先に、ドメインを完成させないためです。期日も、この章では入れませんでした。

### 不変性は、境界で切る必要がない

Kotlin FP 版では、インメモリの取得関数を作るときに、受け取った `Map` と `List` を `copy` しました。外側の `MutableMap` が後から変わっても、渡し済みの結果が変わらないようにするためです。

Flix の `Map` と `List` は不変です。可変な参照を受け取る口がそもそも無いので、**境界で切る処理が要りません。** `Application.Hub.inMemory` は、受け取った `lists` をそのまま閉じ込めています。

## まとめ

- 依存を関数値のレコード `ToDoListHub[ef]` で渡し、効果を引数に取る型の別名にしました。
- 同じ `route` を、効果ハンドラで依存を差し替える形でも書き、レスポンスが一致することを確かめました。ハンドラを忘れると、型検査で拒否されます。
- 連載では、他の対象と形を揃えられる Hub を採りました。効果ハンドラ版は比較のために残しました。
- `ToDoItem` に有限状態を追加し、振る舞いの先回りは避けました。
- `flix test` は 28 本が通ります。

つまずきとして、効果を持たない型を `ToDoListHub[Pure]` と書くとコンパイルが通りませんでした。空の効果は `{}` と書きます。

次章では、変更後の値を直接上書きせず、何が起きたかをイベントとして残します。イベント列を再帰と `fold` で状態へ畳み込み、Flix 標準の `Monoid` で合成します。

## この章で書いたコード

- Hub: `apps/flix/zettai/src/Application.Hub.flix`
- 効果としての依存: `apps/flix/zettai/src/Application.Lookup.flix`
- ルーティング: `apps/flix/zettai/src/Web.Http.flix`
- ドメイン: `apps/flix/zettai/src/Domain.flix`
- テスト: `apps/flix/zettai/test/TestHub.flix`、`TestEffectDi.flix`、`TestDomainModel.flix`

## 参照

- Uberto Barbini『From Objects to Functions』第 4 章
- [Flix 公式ドキュメント](https://doc.flix.dev/)
