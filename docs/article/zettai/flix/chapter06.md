---
type: Article
title: "第 6 章 コマンドを実行してイベントを生成する"
description: "Zettai 連載 Flix 版の第 6 章。利用者の意図をコマンドとして表し、コマンドと現在状態から新しいイベントを決める純粋関数を作る。4 x 4 の遷移表を全 16 組で確かめ、領域の中だけで変わるイベントストアに接続し、コマンド列 781 通りで不変条件を確かめる。"
tags: [article, zettai, flix, command, state-machine, region, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-02T21:00:00Z }
---

# 第 6 章 コマンドを実行してイベントを生成する

前章では、保存済みのイベントから現在状態を導出しました。しかし、イベントを作っていたのはテストだけです。この章では利用者の意図をコマンドにし、コマンドと現在状態から新しいイベントを決めます。

## 新しい ToDo リストの作成

最初の要求は、新しいリストを作ることです。同じ名前のリストがすでにあれば拒否しなければなりません。項目の追加と、項目の状態変更も同じ入口に集めます。この章で作るのはドメインとアプリケーションの部分で、公開済みの HTTP の入口はリストを読む GET だけのままです。

後の章で書き換わったコードのブロックには、この章を書いた時点のコミットを示してあります。

できる操作だけでなく、次の拒否もテストから書きました。

- 既存リストの再作成
- 存在しないリストへの項目追加
- 存在しない項目の状態変更
- 許されない状態遷移

成功例だけなら、すべての操作を許す実装でも Green になります。拒否例は、ドメインの境界を決めるテストです。

## コマンドを使って状態を変更する

利用者の意図を、閉じた型で表します。

```flix
    pub enum ToDoListCommand {
        case CreateToDoList(User, ListName)
        case AddToDoItem(User, ListName, ToDoItem)
        case ChangeItemStatus(User, ListName, String, ToDoStatus)
    }
```

コマンドとイベントは似ていますが、時制と責務が違います。

| | コマンド | イベント |
| :--- | :--- | :--- |
| 意味 | これから行いたい意図 | すでに起きた事実 |
| 名前 | `CreateToDoList` | `ListCreated` |
| 拒否 | できる | しない |
| 保存 | 原則として保存しない | 履歴として保存する |

コマンドは検証され、この章では成功したときだけ 1 件のイベントになります。イベントは再生の入力なので、適用するときに同じ業務判断を繰り返しません。第 5 章の `apply` が、作成されていないリストへの追加を黙って無視したのはそのためです。拒否はコマンドの仕事で、イベントの仕事ではありません。

拒否には理由が要ります。理由は文字列ではなく、判断に使った値を持つ型にしました。

```flix
    pub enum ZettaiError with Eq, ToString {
        case ListNotFound(User, ListName)
        case ListAlreadyExists(User, ListName)
        case ItemNotFound(User, ListName, String)
        case InvalidTransition(String, ToDoStatus, ToDoStatus)
    }
```

テストは文字列を解析せず、`InvalidTransition("write chapter", Todo, Blocked)` という構造を比べられます。

## 状態とイベントによるドメインのモデリング

状態変更を実装する前に、4 x 4 の遷移表を決めました。

| 現在 | → Todo | → InProgress | → Done | → Blocked |
| :--- | :---: | :---: | :---: | :---: |
| Todo | 拒否 | 許可 | 許可 | 拒否 |
| InProgress | 拒否 | 拒否 | 許可 | 許可 |
| Done | 拒否 | 拒否 | 拒否 | 拒否 |
| Blocked | 拒否 | 許可 | 拒否 | 拒否 |

16 組のうち許可は 5 組、拒否は 11 組です。

`Done` からは戻せません。誤って完了した場合も履歴を書き換えず、別の説明で新しい項目を作る業務規則とします。これは Zettai の規則であり、ToDo アプリ一般の規則ではありません。`Todo` から `Blocked` へ直接移せないのは、まだ着手していない項目を「妨げられている」とは扱わず、まず `InProgress` にするためです。

表をそのまま、組に対するパターンマッチに移します。

```flix
    pub def canTransition(current: ToDoStatus, next: ToDoStatus): Bool =
        match (current, next) {
            case (ToDoStatus.Todo, ToDoStatus.InProgress)       => true
            case (ToDoStatus.Todo, ToDoStatus.Done)             => true
            case (ToDoStatus.InProgress, ToDoStatus.Done)       => true
            case (ToDoStatus.InProgress, ToDoStatus.Blocked)    => true
            case (ToDoStatus.Blocked, ToDoStatus.InProgress)    => true
            case _                                              => false
        }
```

テストは代表例ではなく、全 16 組を表として書きます。

```flix
    def table(): List[(ToDoStatus, ToDoStatus, Bool)] = List#{
        (Todo, Todo, false),       (Todo, InProgress, true),       (Todo, Done, true),       (Todo, Blocked, false),
        (InProgress, Todo, false), (InProgress, InProgress, false), (InProgress, Done, true), (InProgress, Blocked, true),
        (Done, Todo, false),       (Done, InProgress, false),      (Done, Done, false),      (Done, Blocked, false),
        (Blocked, Todo, false),    (Blocked, InProgress, true),    (Blocked, Done, false),   (Blocked, Blocked, false)
    }
```

表が全 16 組を覆っていること、許可がちょうど 5 組であることも、別のテストで確かめます。状態を追加したのに表の行を足し忘れると、この 2 本が落ちます。

```flix
    @Test
    def exactlyFiveTransitionsAreAllowed(): Unit \ Assert =
        Assert.assertEq(expected = 5, List.length(List.filter(row -> match row { case (_, _, allowed) => allowed }, table())))
```

つまずきとして、最初はテストの変数名を `from` と `to` にしました。`from` は予約語で、第 5 章と同じパースエラーになります。`current` と `next` に直しました。

成功した状態変更は、新しいイベントになります。

```flix
        case ItemStatusChanged(User, ListName, String, ToDoStatus)
```

適用は、対象の項目だけを新しい状態の項目に置き換えます。

<!-- code-check: ignore この章を書いた時点のコード（コミット 658d9c5）。第 8 章で Domain.changeStatus に置き換えた。同じ説明の項目すべてを変える規則を、最初の 1 件だけを変える規則に直した -->
```flix
    def withStatus(description: String, status: ToDoStatus): ToDoItem -> ToDoItem =
        item -> match item {
            case ToDoItem.ToDoItem(d, _) if d == description => ToDoItem.ToDoItem(d, status)
            case other                                       => other
        }
```

テストでは適用前と適用後を同時に持ち、古い値が変わっていないことを確かめます。

## 関数型ステートマシンを記述する

関数型ステートマシンの中心は、コマンドと現在状態を受け取って、イベントまたは拒否の理由を返す純粋関数です。

```flix
    pub def handle(command: ToDoListCommand, state: ToDoListState): Result[ZettaiError, List[ToDoListEvent]] =
        match command {
            case ToDoListCommand.CreateToDoList(user, name) =>
                match Domain.Events.listFor(state, user, name) {
                    case Some(_) => Err(ZettaiError.ListAlreadyExists(user, name))
                    case None    => Ok(ToDoListEvent.ListCreated(user, name) :: Nil)
                }
            case ToDoListCommand.AddToDoItem(user, name, item) =>
                forM (
                    _ <- requireList(state, user, name)
                ) yield ToDoListEvent.ItemAdded(user, name, item) :: Nil
            case ToDoListCommand.ChangeItemStatus(user, name, description, newStatus) =>
                forM (
                    list    <- requireList(state, user, name);
                    item    <- requireItem(user, name, description, list);
                    allowed <- checkTransition(description, item, newStatus)
                ) yield ToDoListEvent.ItemStatusChanged(user, name, allowed, newStatus) :: Nil
        }
```

`forM` は Flix の内包表記で、`Result` の連鎖を上から下へ読める形で書けます。どれかが `Err` を返した時点で、後続は実行されずにその `Err` が結果になります。`ChangeItemStatus` は「リストがある」「項目がある」「遷移が許される」の 3 つを順に確かめ、最初に失敗した理由を返します。

```flix
    def checkTransition(description: String, item: ToDoItem, newStatus: ToDoStatus): Result[ZettaiError, String] =
        match item {
            case ToDoItem.ToDoItem(_, current) =>
                if (Domain.canTransition(current, newStatus))
                    Ok(description)
                else
                    Err(ZettaiError.InvalidTransition(description, current, newStatus))
        }
```

`handle` は現在状態を引数に取るだけで、保存先を知りません。同じコマンドと同じ状態からは、常に同じ結果が返ります。

テストは拒否の理由を、構造ごと比べます。

```flix
    @Test
    def rejectsATransitionTheTableDoesNotAllow(): Unit \ Assert =
        Assert.assertEq(
            expected = Err(ZettaiError.InvalidTransition("write chapter", ToDoStatus.Todo, ToDoStatus.Blocked)),
            Domain.Commands.handle(ChangeItemStatus(uberto(), book(), "write chapter", ToDoStatus.Blocked), withItem())
        )
```

## ハブと接続する

`handle` を実際に使うには、過去のイベントを読み、現在状態を作り、コマンドを実行し、新しいイベントを書き足す、という流れが要ります。保管場所は、追記だけができるイベントストアとして、関数値のレコードで表します。

<!-- code-check: ignore この章を書いた時点のコード（コミット 658d9c5）。第 9 章で EventStore と CommandHub の戻り値を Result に変えた -->
```flix
    pub type alias EventStore[ef: Eff] = {
        load = Unit -> List[ToDoListEvent] \ ef,
        append = List[ToDoListEvent] -> Unit \ ef
    }
```

第 4 章の Hub と同じく、効果 `ef` は使う側が決めます。インメモリの実装は、**領域**（`region`）の中にだけ存在する可変の `Ref` を使います。

<!-- code-check: ignore この章を書いた時点のコード（コミット 658d9c5）。第 9 章で EventStore と CommandHub の戻り値を Result に変えた -->
```flix
    pub def inMemory(rc: Region[r]): EventStore[r] \ r = {
        let history = Ref.fresh(rc, Nil);
        {
            load = () -> Ref.get(history),
            append = events -> Ref.put(Ref.get(history) ::: events, history)
        }
    }
```

`Ref` は書き換えられる箱です。Flix では、この箱が属する領域 `rc` が型に現れます。領域から箱を持ち出して使おうとすると、型エラーになります。

```bash
>> Unable to unify the effect formulas: 'rc' and 'IO & e0'.
```

メッセージは分かりにくいのですが、領域の効果 `rc` と `IO` が合わない、という意味です。そのため、領域を開いて閉じるまでの処理は、外から見ると純粋な関数として扱えます。

コマンドの入口は、このストアの上に作ります。

<!-- code-check: ignore この章を書いた時点のコード（コミット 658d9c5）。第 9 章で EventStore と CommandHub の戻り値を Result に変えた -->
```flix
    pub def commandHub(store: EventStore[ef]): CommandHub[ef] = {
        execute = command -> {
            let state = Domain.Events.Replay.replayFrom(Domain.Events.empty(), store#load());
            match Domain.Commands.handle(command, state) {
                case Ok(events) => { store#append(events); Ok(()) }
                case Err(e)     => Err(e)
            }
        },
        current = () -> Domain.Events.Replay.replayFrom(Domain.Events.empty(), store#load())
    }
```

拒否されたときは、何も書き足しません。テストでは、領域の中でストアと Hub を作り、コマンドを順に実行して、結果と最終状態と履歴を返します。

<!-- code-check: ignore この章を書いた時点のコード（コミット 658d9c5）。第 9 章で EventStore と CommandHub の戻り値を Result に変えた -->
```flix
    def scenario(commands: List[Domain.Commands.ToDoListCommand]): (List[Result[ZettaiError, Unit]], Option[ToDoList], List[Domain.Events.ToDoListEvent]) = {
        region rc {
            let store = Application.EventStore.inMemory(rc);
            let hub = Application.CommandHub.commandHub(store);
            let results = List.map(hub#execute, commands);
            (results, Domain.Events.listFor(hub#current(), uberto(), book()), store#load())
        }
    }
```

`scenario` の型に `IO` は現れません。内部で箱を書き換えていますが、箱は領域の外に出ないので、型は純粋です。テストごとに領域を開くので、テスト間でストアが残ることもありません。

```flix
    @Test
    def aRejectedCommandLeavesNoEventBehind(): Unit \ Assert = {
        let (results, _, history) = scenario(List#{
            CreateToDoList(uberto(), book()),
            CreateToDoList(uberto(), book())
        });
        Assert.assertEq(expected = List#{Ok(()), Err(ZettaiError.ListAlreadyExists(uberto(), book()))}, results);
        Assert.assertEq(expected = List#{ListCreated(uberto(), book())}, history)
    }
```

## コマンドとイベントを深く知る

個別の例は書けましたが、コマンドの並びが長くなると、例では漏れます。第 5 章と同じく、小さな範囲の全パターンで確かめます。1 人の利用者、1 つのリスト、1 つの項目に絞った 5 種類のコマンドを、長さ 0 から 4 まで並べると 781 通りです。

```flix
    def commands(): List[ToDoListCommand] = List#{
        CreateToDoList(uberto(), book()),
        AddToDoItem(uberto(), book(), Domain.todo("a")),
        ChangeItemStatus(uberto(), book(), "a", ToDoStatus.InProgress),
        ChangeItemStatus(uberto(), book(), "a", ToDoStatus.Done),
        ChangeItemStatus(uberto(), book(), "a", ToDoStatus.Blocked)
    }
```

確かめる性質は 2 つです。

1. 受理されたコマンドは、ちょうど 1 件のイベントを残す
2. `Done` になった項目には、その後の状態変更が続かない

```flix
    @Test
    def eachAcceptedCommandLeavesExactlyOneEvent(): Unit \ Assert =
        List.forEach(sequence -> {
            let (results, history) = execute(sequence);
            Assert.assertEq(expected = List.count(identity, results), List.length(history))
        }, allSequences())
```

```flix
    @Test
    def nothingChangesAfterAnItemIsDone(): Unit \ Assert =
        List.forEach(sequence -> {
            let (_, history) = execute(sequence);
            Assert.assertTrue(nothingFollowsDone(statusesChangedTo(history)))
        }, allSequences())
```

全パターンを走査するテストは、「何も確かめていない」状態でも通ってしまいます。そこで、`Done` に到達する列が実際にあることを、別のテストで確かめます。

```flix
    @Test
    def theSequencesActuallyReachTheDoneState(): Unit \ Assert = {
        let reached = List.count(sequence -> {
            let (_, history) = execute(sequence);
            List.exists(s -> s == ToDoStatus.Done, statusesChangedTo(history))
        }, allSequences());
        Assert.assertTrue(reached > 0)
    }
```

### 壊して確かめる

テストが本当に壊れた実装を検出できるのか、遷移表を 2 通りに壊して確かめました。

| 壊し方 | 落ちたテスト |
| :--- | :--- |
| `Done` → `Todo` を許可する | 遷移表のテスト 1 本だけ。不変条件のテストは通った |
| `Done` → `InProgress` を許可する | 遷移表のテストと、`Done` の後に変更が続かないことのテスト |

1 つ目で不変条件のテストが通ったのは、コマンドの全体に `Todo` への変更が含まれないからです。**全パターンは、並べた範囲の中でしか確かめません。** 遷移表のテストが、範囲の外を受け持っています。2 つのテストは重なりを持って守っていて、どちらかが他方の代わりになるのではありません。

どちらも確かめたあと、元に戻しています。

## まとめ

- 利用者の意図を `ToDoListCommand` で表し、失敗の理由を `ZettaiError` の構造にしました。
- 4 x 4 の遷移表を、全 16 組と許可の数 5 で確かめました。
- `handle` を、コマンドと現在状態から `Result[ZettaiError, List[ToDoListEvent]]` を返す純粋関数にしました。`forM` で、確認の連鎖を読みやすく書けました。
- イベントストアを関数値のレコードで表し、領域の中の `Ref` で実装しました。ストアを使うシナリオの型は、純粋なままです。
- 781 通りのコマンド列で、2 つの不変条件を確かめました。遷移表を壊すと検出できることも確かめました。
- `flix test` は 58 本が通ります。

つまずきとして、`from` と `run` が予約語である点は、第 5 章に続いて再び当たりました。コンパイラは名前の位置のパースエラーを報告するだけなので、予約語の一覧を先に頭に入れておくほうが早いです。

次章では、`Result` に載せた失敗を、HTTP の応答へ写します。`Result` を作らずに標準のものを使う判断と、その判断で何を確かめたかを記録します。

## この章で書いたコード

- コマンドと `handle`: `apps/flix/zettai/src/Domain.Commands.flix`
- 失敗の型: `apps/flix/zettai/src/Domain.Errors.flix`
- 遷移表: `apps/flix/zettai/src/Domain.flix`
- イベントストア: `apps/flix/zettai/src/Application.EventStore.flix`
- コマンドの入口: `apps/flix/zettai/src/Application.CommandHub.flix`
- テスト: `apps/flix/zettai/test/TestCommands.flix`、`TestStatusRules.flix`、`TestCommandHub.flix`、`TestCommandInvariants.flix`

## 参照

- Uberto Barbini『From Objects to Functions』第 6 章
- [Flix 公式ドキュメント](https://doc.flix.dev/)
