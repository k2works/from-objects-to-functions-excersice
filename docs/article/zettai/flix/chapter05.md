---
type: Article
title: "第 5 章 イベントで状態を変更する"
description: "Zettai 連載 Flix 版の第 5 章。ToDo リストの変更をイベントとして残し、再帰と foldLeft で状態へ畳み込み、状態変換を Flix 標準の SemiGroup / Monoid のインスタンスにする。法則は乱数ではなく小さな範囲の全パターンで確かめる。"
tags: [article, zettai, flix, event-sourcing, monoid, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-02T19:00:00Z }
---

# 第 5 章 イベントで状態を変更する

前章では、依存を関数値のレコードで渡しました。この章では、ToDo リストの変更そのものを関数で扱います。現在の状態を上書きせず、起きた出来事を不変の列に残し、その列から状態を計算します。

後の章で書き換わったコードのブロックには、この章を書いた時点のコミットを示してあります。

## ToDo リストの作成の表示

最初の出来事は、リストが作られたことです。命令の `CreateList` ではなく、すでに起きた事実なので、過去形の `ListCreated` と名付けます。

<!-- code-check: ignore この章を書いた時点のコード（コミット 530b4a8）。第 6 章で ItemStatusChanged を追加した -->
```flix
    pub enum ToDoListEvent with Eq, ToString {
        case ListCreated(User, ListName)
        case ItemAdded(User, ListName, ToDoItem)
    }
```

項目の追加も出来事です。`enum` は生成後に内容を変えられず、`match` はすべてのケースを列挙しなければコンパイルが通りません。この時点では `ListCreated` と `ItemAdded` の 2 種類だけで、状態変更や削除は先回りして足しません。

## 状態変更の保存

通常の CRUD は、保存先の値を上書きします。簡単ですが、前の状態と「なぜ変わったか」を捨てます。ここでは、出来事を保存する対象とし、現在の状態は必要なときに導出します。

導出先の状態も値です。

```flix
    pub enum ToDoListState(Map[(User, ListName), ToDoList]) with Eq, ToString
```

キーは `(User, ListName)` の組です。Flix では組にも `Order` が自動で備わっているので、そのまま `Map` のキーにできます。

1 つの出来事を適用する関数は、新しい状態を返します。

<!-- code-check: ignore この章を書いた時点のコード（コミット 530b4a8）。第 6 章で ItemStatusChanged を追加した -->
```flix
    pub def apply(state: ToDoListState, event: ToDoListEvent): ToDoListState =
        match event {
            case ToDoListEvent.ListCreated(user, name) =>
                put(state, user, name, ToDoList.ToDoList(name, Nil))
            case ToDoListEvent.ItemAdded(user, name, item) =>
                match listFor(state, user, name) {
                    case Some(ToDoList.ToDoList(_, items)) =>
                        put(state, user, name, ToDoList.ToDoList(name, items ::: item :: Nil))
                    case None => state
                }
        }
```

`Map.insert` も `:::` も元の値を書き換えません。テストでは、空の状態、作成後、追加後の 3 つを同時に持ち、前の 2 つが変わっていないことを確かめます。

```flix
    @Test
    def applyingAnEventDoesNotChangeThePreviousState(): Unit \ Assert = {
        let empty = Domain.Events.empty();
        let created = Domain.Events.apply(empty, ListCreated(uberto(), book()));
        let added = Domain.Events.apply(created, ItemAdded(uberto(), book(), Domain.todo("write chapter")));
        Assert.assertEq(expected = None, Domain.Events.listFor(empty, uberto(), book()));
        Assert.assertEq(expected = Some(ToDoList.ToDoList(book(), Nil)), Domain.Events.listFor(created, uberto(), book()));
        Assert.assertEq(expected = Some(ToDoList.ToDoList(book(), List#{Domain.todo("write chapter")})), Domain.Events.listFor(added, uberto(), book()))
    }
```

Kotlin FP 版では、状態を作るときに `copy` で別名を切る必要がありました。Flix の `Map` と `List` は不変なので、この処理は要りません。

作成されていないリストへの `ItemAdded` は、状態を変えずに返します。

```flix
    @Test
    def anItemAddedToAMissingListIsIgnored(): Unit \ Assert = {
        let state = Domain.Events.apply(Domain.Events.empty(), ItemAdded(uberto(), book(), Domain.todo("write chapter")));
        Assert.assertEq(expected = None, Domain.Events.listFor(state, uberto(), book()))
    }
```

これは、不正な履歴を拒否する仕組みではありません。出来事の列が正しいかどうかを検証するのは、コマンドを導入する第 6 章の仕事です。この章の `apply` は、渡された出来事を決定的に適用することだけに集中します。

## 再帰の力を解き放つ

1 件を適用できれば、列全体も適用できます。まず標準関数を使わず、再帰の形を見せます。

```flix
    pub def replayByRecursion(initial: ToDoListState, events: List[ToDoListEvent]): ToDoListState =
        match events {
            case Nil     => initial
            case e :: es => replayByRecursion(Domain.Events.apply(initial, e), es)
        }
```

空なら初期状態を返します。空でなければ、先頭の出来事を適用した状態と、残りの出来事で同じ関数を呼びます。終了条件と、1 つ進めて残りを処理する形です。

つまずきとして、最初は引数名を `from` にしました。`from` は Flix の予約語で、「`<parameter>` が来るはずです」というパースエラーになります。`initial` に改名しました。

## イベントを畳み込む

再帰の定型を、標準の `List.foldLeft` に置き換えます。

```flix
    pub def replayFrom(initial: ToDoListState, events: List[ToDoListEvent]): ToDoListState =
        List.foldLeft(Domain.Events.apply, initial, events)
```

`foldLeft` は初期値から始め、左から順に出来事を現在の状態へ取り込みます。可変の蓄積変数は見えません。各ステップは古い状態を受け取り、新しい状態を返します。

再帰版と `foldLeft` 版が同じ状態を返すことを、テストで確かめます。

```flix
    @Test
    def recursionAndFoldGiveTheSameState(): Unit \ Assert =
        List.forEach(seed ->
            List.forEach(events ->
                Assert.assertEq(expected = Domain.Events.Replay.replayByRecursion(seed, events), Domain.Events.Replay.replayFrom(seed, events)),
                Gen.eventListsUpTo(3)),
            seeds())
```

ここで使っている `Gen.eventListsUpTo(3)` は、乱数ではありません。

### 乱数ではなく、全パターンを並べる

Kotlin FP 版は、プロパティテストのライブラリに生成を任せました。Flix 版では、プロパティテストのライブラリを調べていません（標準の道具だけで書けるかを先に確かめるため）。代わりに、**小さな範囲のすべてのパターンを並べる**ことにしました。

```flix
    pub def universe(): List[ToDoListEvent] = {
        let users = List#{User.User("uberto"), User.User("alice")};
        let names = List#{ListName.ListName("book"), ListName.ListName("food")};
        let items = List#{Domain.todo("a"), Domain.todo("b")};
        let created = List.flatMap(u -> List.map(n -> ListCreated(u, n), names), users);
        let added = List.flatMap(u -> List.flatMap(n -> List.map(i -> ItemAdded(u, n, i), items), names), users);
        created ::: added
    }
```

2 人の利用者、2 つのリスト名、2 つの項目から、起こりうる出来事は 12 種類です。これを長さ 0 から n までの列に並べます。

```flix
    def ofLength(k: Int32, xs: List[a]): List[List[a]] =
        if (k <= 0)
            Nil :: Nil
        else
            List.flatMap(x -> List.map(rest -> x :: rest, ofLength(k - 1, xs)), xs)
```

長さ 3 までで 1,885 通りです。乱数と違い、**この範囲のどこにも反例がないことが言えます**。一方で、範囲の外は何も言えません。ユーザーが 3 人いる場合や、長さ 4 の列は確かめていません。乱数は広い範囲を薄く、全パターンは狭い範囲を厚く確かめます。どちらが良いかは、壊れ方の見当次第です。この章の法則は、少数の要素の組み合わせで破れるものが多いので、全パターンを選びました。

## モノイドの発見

出来事を別の角度から見ます。出来事を 1 つ固定すると、「状態を受け取り、状態を返す関数」になります。

```flix
    pub enum StateTransition(ToDoListState -> ToDoListState)
```

```flix
    pub def fromEvent(event: ToDoListEvent): StateTransition =
        StateTransition.StateTransition(state -> Domain.Events.apply(state, event))
```

2 つの状態変換は、順番につなげられます。何もしない変換もあります。

```flix
    pub def identity(): StateTransition =
        StateTransition.StateTransition(state -> state)

    /// 順番につなぐ。a の後に b を行う。
    pub def andThen(a: StateTransition, b: StateTransition): StateTransition =
        StateTransition.StateTransition(state -> applyTo(b, applyTo(a, state)))
```

つなぎ方と何もしない変換がそろい、2 つの法則が成り立つ構造を**モノイド**と呼びます。

- 結合律: `(a andThen b) andThen c` と `a andThen (b andThen c)` の結果は同じです。
- 単位元: `identity` を左右どちらにつないでも結果は変わりません。

Flix の標準ライブラリには、この構造を表す型クラス `SemiGroup` と `Monoid` が最初からあります。`StateTransition` にインスタンスを与えるだけで、標準の道具が使えます。

```flix
    instance SemiGroup[StateTransition] {
        pub def combine(a: StateTransition, b: StateTransition): StateTransition = andThen(a, b)
    }

    instance Monoid[StateTransition] {
        pub def empty(): StateTransition = identity()
    }
```

Kotlin FP 版では、`andThen` と `identity` を自分で書き、法則はテストで確かめるだけでした。Flix では、**構造を型クラスとして宣言できる**ので、標準の `Monoid.fold` が使えます。

```flix
    pub def fromEvents(events: List[ToDoListEvent]): StateTransition =
        Monoid.fold(List.map(fromEvent, events))
```

出来事の列を、1 つの状態変換にまとめます。

### 法則を全パターンで確かめる

型クラスのインスタンスは、法則を守ることを**コンパイラは確かめません**。確かめるのはテストです。

```flix
    @Test
    def associativity(): Unit \ Assert =
        List.forEach(a ->
            List.forEach(b ->
                List.forEach(c ->
                    Assert.assertTrue(sameOnEverySeed(
                        Domain.Events.Transition.andThen(Domain.Events.Transition.andThen(a, b), c),
                        Domain.Events.Transition.andThen(a, Domain.Events.Transition.andThen(b, c)))),
                    transitions()),
                transitions()),
            transitions())
```

```flix
    @Test
    def identityOnTheLeftAndOnTheRight(): Unit \ Assert =
        List.forEach(t -> {
            Assert.assertTrue(sameOnEverySeed(Domain.Events.Transition.andThen(Domain.Events.Transition.identity(), t), t));
            Assert.assertTrue(sameOnEverySeed(Domain.Events.Transition.andThen(t, Domain.Events.Transition.identity()), t))
        }, transitions())
```

関数は `==` で比べられないので、**用意した初期状態のすべてで同じ結果を返す**ことを、等しいことの定義にしました。

```flix
    def sameOnEverySeed(a: StateTransition, b: StateTransition): Bool =
        List.forAll(seed -> Domain.Events.Transition.applyTo(a, seed) == Domain.Events.Transition.applyTo(b, seed), seeds())
```

畳み込んだ変換が、出来事を順に適用した結果と等しいことも確かめます。

```flix
    @Test
    def foldingTransitionsEqualsReplayingEvents(): Unit \ Assert =
        List.forEach(events ->
            List.forEach(seed ->
                Assert.assertEq(
                    expected = Domain.Events.Replay.replayFrom(seed, events),
                    Domain.Events.Transition.applyTo(Domain.Events.Transition.fromEvents(events), seed)),
                seeds()),
            Gen.eventListsUpTo(3))
```

法則のテストが、本当に壊れた実装を検出できるのかも確かめました。`identity` を「常に空の状態を返す」関数に書き換えると、単位元のテストと畳み込みの等価のテストの 2 本が落ちました。元に戻すと、すべて通ります。

### 交換律は成り立たない

モノイドには、**交換律を求められません**。順番が結果を変える場合があり、それが正しい動作です。

```flix
    @Test
    def theOrderOfCompositionMatters(): Unit \ Assert = {
        let created = Domain.Events.Transition.fromEvent(ToDoListEvent.ListCreated(User.User("uberto"), ListName.ListName("book")));
        let added = Domain.Events.Transition.fromEvent(ToDoListEvent.ItemAdded(User.User("uberto"), ListName.ListName("book"), Domain.todo("a")));
        Assert.assertFalse(sameOnEverySeed(Domain.Events.Transition.andThen(created, added), Domain.Events.Transition.andThen(added, created)))
    }
```

「作成してから追加」と「追加してから作成」は結果が違います。後者では、リストがないまま追加が無視されるからです。

結合律があると、長い出来事の列を分割して別々に畳み込み、後で合成しても結果が変わりません。

```flix
    @Test
    def replayingInTwoPartsGivesTheSameStateAsReplayingAtOnce(): Unit \ Assert =
        List.forEach(a ->
            List.forEach(b ->
                Assert.assertEq(
                    expected = Domain.Events.Replay.replayFrom(Domain.Events.empty(), a ::: b),
                    Domain.Events.Replay.replayFrom(Domain.Events.Replay.replayFrom(Domain.Events.empty(), a), b)),
                Gen.eventListsUpTo(2)),
            Gen.eventListsUpTo(2))
```

これは、後の章で出来事の列が長くなったとき、並べ替えずに分割して処理できる根拠になります。

## まとめ

- 変更を出来事として残し、現在の状態を畳み込みで導出しました。
- 再帰版と `List.foldLeft` 版が同じ状態を返すことを、小さな範囲の全パターンで確かめました。
- 状態変換を Flix 標準の `SemiGroup` / `Monoid` のインスタンスにし、`Monoid.fold` で出来事の列をまとめました。
- 結合律、単位元、畳み込みの等価を確かめ、`identity` を壊すとテストが落ちることも確かめました。
- 交換律は成り立たず、順番が結果を変えることをテストにしました。
- `flix test` は 40 本が通ります。

つまずきとして、`from` と `run` は Flix の予約語で、引数名や関数名に使えませんでした。`run` は `Domain.Events.Transition.run` と書いた箇所が「`,` が来るはずです」というパースエラーになり、原因に気づきにくいものでした。`initial` と `applyTo` に改名しました。

次章では、命令を実行して出来事を生成します。出来事の列が正しいかをコマンドが検証し、状態遷移を代数的データ型で表します。

## この章で書いたコード

- イベントと状態: `apps/flix/zettai/src/Domain.Events.flix`
- 畳み込み: `apps/flix/zettai/src/Domain.Events.Replay.flix`
- 状態変換のモノイド: `apps/flix/zettai/src/Domain.Events.Transition.flix`
- 全パターンの生成: `apps/flix/zettai/test/Gen.flix`
- テスト: `apps/flix/zettai/test/TestEvents.flix`、`TestReplay.flix`、`TestTransition.flix`

## 参照

- Uberto Barbini『From Objects to Functions』第 5 章
- [Flix 公式ドキュメント](https://doc.flix.dev/)
