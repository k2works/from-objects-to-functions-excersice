---
type: Article
title: "第 8 章 ファンクタを使ってイベントを射影する"
description: "Zettai 連載 Flix 版の第 8 章。イベントを、状態ではなく画面向けの行へ畳み込む。状態への適用と射影で食い違っていた規則を共有し、射影が再生した状態の数え直しと一致することを全パターンで確かめる。読み取り専用の QueryHub で CQRS に到達する。"
tags: [article, zettai, flix, projection, cqrs, functor, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-02T23:00:00Z }
---

# 第 8 章 ファンクタを使ってイベントを射影する

前章で失敗を型に載せました。この章では、表示のしかたを見直します。

きっかけは困りごとです。**ドメインの状態と、画面が欲しい形が違う**のです。

後の章で書き換わったコードのブロックには、この章を書いた時点のコミットを示してあります。

## イベントを射影する

第 5 章から、状態を `ToDoListState` という 1 つの型で持ってきました。コマンドを判断するとき、この形は都合がよいです。「同名のリストがすでにあるか」「その項目は今どの状態か」を判断する情報がそろっています。

### 画面が欲しいものは違う

利用者に「リストの一覧」を見せたいとします。画面に出したいのは、たとえばこうです。

| リスト名 | 項目数 | 完了 |
| :--- | :--- | :--- |
| book | 3 | 1 |
| shop | 5 | 5 |

`ToDoListState` から作れます。ただし画面ごとに状態から読み替えるコードを書くと、同じような変換が散らばります。そして**状態を持つ理由が 2 つ混ざります**。

| 状態を持つ理由 | 必要な形 |
| :--- | :--- |
| 業務ルールを判断する（コマンド側） | 判断に必要な情報がそろっている形 |
| 画面に見せる（クエリ側） | 表示したい形 |

1 つの型で両方を満たそうとすると、どちらにも最適でない形になります。

### 表示用のモデルを別に作る

表示のためのモデルを別に持ちます。

```flix
    pub enum ToDoListRow(ListName, Int32, Int32) with Eq, ToString
```

リスト名、項目数、完了数です。この行を、どこから作るか。**イベントから作ります。**

### 第 5 章と同じ形

第 5 章の `replayFrom` を思い出してください。

```flix
    pub def replayFrom(initial: ToDoListState, events: List[ToDoListEvent]): ToDoListState =
        List.foldLeft(Domain.Events.apply, initial, events)
```

射影も同じ `foldLeft` です。

```flix
    pub def projectFrom(user: User, events: List[ToDoListEvent]): List[ToDoListRow] =
        events
            |> List.filter(e -> owner(e) == user)
            |> List.foldLeft(step, Map#{})
            |> Map.toList
            |> List.map(toRow)
```

**違うのは畳み込む先だけです。** 同じイベントの列を、状態ではなく行へ畳み込みます。状態を上書きしていたら、あとから別の形が欲しくなったときに作り直せません。イベントが残っているので、いくつでも射影を作れます。

### 射影を実装する

畳み込みの途中経過は、リスト名から項目の列への対応です。

```flix
    def step(lists: Lists, event: ToDoListEvent): Lists =
        match event {
            case ToDoListEvent.ListCreated(_, name) => Map.insert(name, Nil, lists)
            case ToDoListEvent.ItemAdded(_, name, item) =>
                match Map.get(name, lists) {
                    case Some(items) => Map.insert(name, items ::: item :: Nil, lists)
                    case None        => lists
                }
            case ToDoListEvent.ItemStatusChanged(_, name, description, status) =>
                match Map.get(name, lists) {
                    case Some(items) => Map.insert(name, Domain.changeStatus(items, description, status), lists)
                    case None        => lists
                }
        }
```

作成されていないリストへの `ItemAdded` は無視します。最後に、各リストの項目数と完了数を数えて行にします。

```flix
    def toRow(entry: (ListName, List[ToDoItem])): ToDoListRow =
        match entry {
            case (name, items) => ToDoListRow.ToDoListRow(name, List.length(items), List.count(isDone, items))
        }
```

行の並びは、リスト名の昇順です。Flix の `Map` が、キーの順序で並べて持つためです。

### 規則を共有して食い違いを直す

射影を書くとき、状態の `apply` と射影の両方に「項目の状態を変える」規則が要ることに気づきました。ところが、第 6 章で書いた実装を確かめると、**食い違い**がありました。

- コマンドの `handle` は、説明が一致する項目のうち**最初の 1 件**で、遷移が許されるかを確かめる
- 状態の `apply` は、説明が一致する**すべての項目**の状態を変える

同じ説明の項目が 2 つあると、先頭の項目で遷移を確かめたのに、2 つ目の項目まで変わってしまいます。第 6 章のテストは、同じ説明の項目が 1 つしかない場合だけでした。

テストを先に書くと、本物の Red になりました。

```flix
    @Test
    def aStatusChangeAppliesOnlyToTheFirstItemWithThatDescription(): Unit \ Assert = {
        use Domain.Events.ToDoListEvent.ItemStatusChanged;
        let events = List#{
            ListCreated(uberto(), book()),
            ItemAdded(uberto(), book(), Domain.todo("a")),
            ItemAdded(uberto(), book(), Domain.todo("a")),
            ItemStatusChanged(uberto(), book(), "a", Domain.ToDoStatus.Done)
        };
        let state = List.foldLeft(Domain.Events.apply, Domain.Events.empty(), events);
        Assert.assertEq(
            expected = Some(ToDoList.ToDoList(book(), List#{
                Domain.ToDoItem.ToDoItem("a", Domain.ToDoStatus.Done),
                Domain.todo("a")
            })),
            Domain.Events.listFor(state, uberto(), book())
        )
    }
```

規則を小さな関数に抜き出し、状態の適用と射影の両方がそれを呼ぶようにしました。

```flix
    pub def changeStatus(items: List[ToDoItem], description: String, status: ToDoStatus): List[ToDoItem] =
        match items {
            case Nil => Nil
            case ToDoItem.ToDoItem(d, _) :: rest if d == description => ToDoItem.ToDoItem(d, status) :: rest
            case item :: rest => item :: changeStatus(rest, description, status)
        }
```

それでも、畳み込みの骨格は、状態と射影で 2 か所に残ります。2 つが一致することは、あとで全パターンのテストにします。

## ファンクタに対するクエリの実行

射影した結果を、画面向けの別の値に変換することを考えます。取り出した行に `map` をかけます。

この章の時点では、読み出しは失敗しません。第 9 章でデータベースから読むようになると、読み出しが失敗しうるようになり、クエリの結果は `Result[ZettaiError, List[ToDoListRow]]` になります。第 7 章の `Result` の中に `List` が入る形です。その二重の文脈でも `map` の法則が保たれることを、先に確かめておきます。

```flix
    type alias QueryResult = Result[ZettaiError, List[ToDoListRow]]
```

`Result.map` で中の `List` を取り出し、さらに `List.map` で各行を変換します。`map` が二重になります。

```flix
    def mapRows(f: ToDoListRow -> b, result: Result[ZettaiError, List[ToDoListRow]]): Result[ZettaiError, List[b]] =
        Result.map(rows -> List.map(f, rows), result)
```

### 同じ法則が成り立つ

第 7 章で確かめた恒等則と合成則を、この二重の `map` で確かめます。成功の値は、射影の全パターン（長さ 2 まで）から作った行の列、失敗は 2 種類です。

```flix
    @Test
    def theNestedMapPreservesIdentity(): Unit \ Assert =
        List.forEach(r -> Assert.assertEq(expected = r, mapRows(row -> row, r)), results())
```

```flix
    @Test
    def theNestedMapComposes(): Unit \ Assert =
        List.forEach(r ->
            List.forEach(f ->
                List.forEach(g ->
                    Assert.assertEq(
                        expected = mapRows(row -> g(f(row)), r),
                        Result.map(values -> List.map(g, values), mapRows(f, r))),
                    intToText()),
                rowToInt()),
            results())
```

失敗は、二重の層をそのまま通り抜けます。

```flix
    @Test
    def aFailureFlowsThroughBothLayersUntouched(): Unit \ Assert = {
        let failure: QueryResult = Err(ZettaiError.ListNotFound(User.User("uberto"), ListName.ListName("book")));
        Assert.assertEq(expected = failure, mapRows(row -> row, failure))
    }
```

これらのテストは、書いた時点ですでに通りました。確かめているのは標準の `map` の性質で、新しく作った実装ではないからです。**Red を確認できないテストは、何かを確かめる前に、確かめる対象が実装でないことを自覚しておく必要があります。** ここでは、重ねても法則が保たれるという事実を、あとの章の前提として固定する意味で残しました。

## ファンクタの観点から考察する

ここまでで、`map` が出てくる型が並びました。

| 型 | `map` の意味 | 章 |
| :--- | :--- | :--- |
| `List[a]` | 各要素に適用する | 標準ライブラリ |
| `Result[e, a]` | 成功なら適用し、失敗はそのまま流す | 7 |
| `Result[e, List[a]]` | 成功なら各要素に適用する | 8 |

どれも恒等則と合成則を満たします。ファンクタを 2 つ重ねても、法則は保たれます。重ねた `map` を 1 回にまとめられるのは、この法則があるからです。

Kotlin 版は、`ToDoListProjection` に自作の `map` を持たせ、その法則を確かめました。Flix 版では、ファンクタ自体を作りません。標準の `List.map` と `Result.map` に委ね、確かめるのは「重ねても法則が保たれる」ことと、「射影の結果が再生した状態と一致する」ことです。

一致は、全パターンで確かめます。2 人の利用者、1 つのリスト、1 つの項目、2 つの状態から作った起こりうるイベント 8 種類を、長さ 4 まで並べます。

```flix
    @Test
    def theProjectionMatchesTheCountsOfTheReplayedState(): Unit \ Assert =
        List.forEach(events ->
            List.forEach(user ->
                Assert.assertEq(expected = rowsFromState(user, events), Domain.Queries.Projection.projectFrom(user, events)),
                List#{uberto(), alice()}),
            Gen.projectionListsUpTo(4))
```

`rowsFromState` は、イベントを再生した状態から、行を数え直したものです。

```flix
    def rowsFromState(user: User, events: List[ToDoListEvent]): List[ToDoListRow] = {
        let state = Domain.Events.Replay.replayFrom(Domain.Events.empty(), events);
        match Domain.Events.listFor(state, user, book()) {
            case None => Nil
            case Some(Domain.ToDoList.ToDoList(name, items)) =>
                List#{ToDoListRow.ToDoListRow(
                    name,
                    List.length(items),
                    List.count(item -> match item { case Domain.ToDoItem.ToDoItem(_, s) => s == ToDoStatus.Done }, items)
                )}
        }
    }
```

このテストが本当に食い違いを検出できるのか、射影の規則を壊して確かめました。`Domain.changeStatus` を使わず、「同じ説明の項目すべてを変える」処理に書き換えると、このテストと、同じ説明の項目の個別テストの 2 本が落ちました。元に戻すと、すべて通ります。

## コマンドクエリ責務分離(CQRS)

ここまでで、書く側と読む側が分かれました。

- 書く側: コマンドを受け取り、状態を見て、イベントを追記する（第 6 章の `CommandHub`）
- 読む側: イベントを、画面向けの行に畳み込む（この章の `QueryHub`）

読む側の入口は、読み取り専用です。

<!-- code-check: ignore この章を書いた時点のコード（コミット 37daacd）。第 9 章で EventStore と QueryHub の戻り値を Result に変えた -->
```flix
    pub type alias QueryHub[ef: Eff] = {
        listRows = User -> List[ToDoListRow] \ ef
    }
```

<!-- code-check: ignore この章を書いた時点のコード（コミット 37daacd）。第 9 章で EventStore と QueryHub の戻り値を Result に変えた -->
```flix
    pub def queryHub(store: EventStore[ef]): QueryHub[ef] = {
        listRows = user -> Domain.Queries.Projection.projectFrom(user, store#load())
    }
```

`QueryHub` のレコードには、コマンドを実行するフィールドがありません。**読む側から書く操作を呼ぶことは、型として書けません。** `QueryHub` に `execute` を呼ぼうとすると、コンパイルが次のように拒否します。

```bash
>> Unable to unify the types: '(  )' and '( execute = ToDoListCommand -> t1 \ e0 | r2 )'.
```

`QueryHub` の型 `{ listRows = ... }` に、`execute` が無いという意味です。

2 つの入口は、同じイベントストアを、別々の窓から見ます。

<!-- code-check: ignore この章を書いた時点のコード（コミット 37daacd）。第 9 章で EventStore と QueryHub の戻り値を Result に変えた -->
```flix
    def rowsAfter(commands: List[Domain.Commands.ToDoListCommand]): List[ToDoListRow] = {
        region rc {
            let store = Application.EventStore.inMemory(rc);
            let commandHub = Application.CommandHub.commandHub(store);
            let queryHub = Application.QueryHub.queryHub(store);
            List.forEach(c -> { let _ = commandHub#execute(c); () }, commands);
            queryHub#listRows(uberto())
        }
    }
```

書いた結果は、読む側から見えます。拒否されたコマンドは、読む側にも現れません。

<!-- code-check: ignore この章を書いた時点のコード（コミット 37daacd）。第 9 章で EventStore と QueryHub の戻り値を Result に変えた -->
```flix
    @Test
    def aRejectedCommandDoesNotShowUpInTheRows(): Unit \ Assert =
        Assert.assertEq(
            expected = List#{ToDoListRow.ToDoListRow(book(), 0, 0)},
            rowsAfter(List#{CreateToDoList(uberto(), book()), CreateToDoList(uberto(), book())})
        )
```

2 度目の作成は拒否されるので、行は 1 つのままです。

この分離で得たものは、**表示の都合で、業務の判断を壊さない**ことです。画面に別の列が欲しくなったら、`ToDoListRow` と `projectFrom` だけを変えます。コマンドの判断や、状態の型には触れません。

一方で、この版の `QueryHub` は、呼ぶたびにイベントを頭から畳み込み直します。イベントが増えると、読み取りが遅くなります。畳み込んだ結果を保存しておく仕組みは、この章では作っていません。永続化を扱う第 9 章で、改めて考えます。

## まとめ

- 状態を判断のための形、行を表示のための形として分け、行をイベントから畳み込みました。
- 状態への適用と射影の間にあった「同じ説明の項目」の扱いの食い違いを、テストで見つけ、共有する関数 `Domain.changeStatus` に直しました。
- `Result` と `List` を重ねた `map` が、恒等則と合成則を保つことを確かめました。
- 射影の結果が、再生した状態から数え直した行と一致することを、起こりうるイベント列の全パターンで確かめました。規則を壊すと検出できることも確かめました。
- 読み取り専用の `QueryHub` を `CommandHub` と並べ、型として書く側と読む側を分けました。
- `flix test` は 79 本が通ります。

次章では、イベントストアをデータベースに替えます。JDBC を Java 相互運用で呼び、**効果の保証が切れる場所**がもう 1 つ増えます。

## この章で書いたコード

- 射影: `apps/flix/zettai/src/Domain.Queries.Projection.flix`
- 読み取り専用の入口: `apps/flix/zettai/src/Application.QueryHub.flix`
- 共有する規則: `apps/flix/zettai/src/Domain.flix`
- テスト: `apps/flix/zettai/test/TestProjection.flix`、`TestQueryHub.flix`、`TestQueryFunctor.flix`、`TestEvents.flix`

## 参照

- Uberto Barbini『From Objects to Functions』第 8 章
- [Flix 公式ドキュメント](https://doc.flix.dev/)
