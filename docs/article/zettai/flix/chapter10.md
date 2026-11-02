---
type: Article
title: "第 10 章 コンテキストを読み込み、コマンドを処理する"
description: "Zettai 連載 Flix 版の第 10 章。読んだ後に別の書き込みが入る競合を手順を明示して再現し、版つきの追記と一意制約で守る。コマンドの処理に必要な文脈（接続とトランザクション）を、引数ではなく効果ハンドラで持ち回る。"
tags: [article, zettai, flix, transaction, effect-handler, optimistic-locking, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-03T02:00:00Z }
---

# 第 10 章 コンテキストを読み込み、コマンドを処理する

前章で、イベントを PostgreSQL に追記しました。ただし、前章のストアは、操作ごとに接続を開いて閉じていました。コマンド 1 つの処理は、読む、判断する、書くという 3 つの別々の操作になります。

この章では、その間に何が入り込めるかを、先に再現します。そのあとで、コマンドの処理に必要な**文脈**（接続とトランザクション）を、どう持ち回るかを考えます。Flix 版の問いは、文脈を引数ではなく**効果ハンドラ**で渡せるかです。

後の章で書き換わったコードのブロックには、この章を書いた時点のコミットを示してあります。

## モナドでデータベースにアクセスする

### 操作ごとに接続が作られる

前章のコマンドの入口は、こう動いていました。

1. 履歴を読む（接続を開いて、閉じる）
2. 履歴を元にコマンドを判断する
3. イベントを書く（接続を開いて、閉じる）

同じリストに対するコマンドが 2 つ、ほぼ同時に走るとどうなるでしょうか。2 つとも「リストがない」履歴を読み、2 つとも「作成できる」と判断し、2 つとも書きます。**同じ名前のリストが 2 回作られます。**

前章では、これを推論として書きました。この章では、再現します。

### トランザクションだけで足りるのか

直し方として最初に思いつくのは、読む、判断する、書くをトランザクションに入れることです。PostgreSQL の既定の隔離水準は READ COMMITTED で、他のトランザクションがコミットした内容は、次の問い合わせから見えます。つまり、読んだ後にコミットされた書き込みは、判断した側からは見えません。

見立てを、手順を明示して確かめました。時間や並行実行には頼りません。2 つの接続で、どちらも先に数を読み、そのあとで両方が書きます。

```flix
    def bothReadThenBothWrite(config: PostgresStore.Config, table: String): Int32 \ IO = {
        let a = PostgresStore.connect(config);
        let b = PostgresStore.connect(config);
        a.setAutoCommit(false);
        b.setAutoCommit(false);
        let readA = count(a, table);
        let readB = count(b, table);
        insert(a, table, readA + 1);
        a.commit();
        insert(b, table, readB + 1);
        b.commit();
        a.close();
        b.close();
        count(PostgresStore.connect(config), table)
    }
```

版の列に制約が無いテーブルでは、**どちらもコミットできました。**

```flix
    @Test
    def aTransactionAloneDoesNotStopTwoStaleWritersFromBothCommitting(): Unit \ {Assert, IO} = {
        let config = PostgresStore.configFromEnv();
        let table = PostgresStore.freshTableName();
        let _ = PostgresStore.execute(config, "create table ${table} (version integer not null)");
        // 版に制約が無いと、どちらも「1 番目の書き込み」としてコミットできてしまう
        Assert.assertEq(expected = 2, bothReadThenBothWrite(config, table));
        let _ = PostgresStore.dropTable(config, table);
        ()
    }
```

どちらも「1 番目」として書いたのに、2 行が入っています。トランザクションは、書き込みを一括で確定させる道具です。**読んだ時点から書く時点までの間に、他の書き込みが入らないことは保証しません。**

## ContextReader を使用したコマンドの処理

Kotlin 版は、接続を持ち回る計算を `ContextReader` という型で自作しました。Kotlin FP 版は、環境を関数の引数として渡しました。

Flix には、もう 1 つの道があります。文脈を**効果**として宣言し、実装を**ハンドラ**で与える方法です。第 4 章で、依存を効果で差し替える形を見ました。同じ道具を、接続とトランザクションに使います。

### 版を確かめる

効果の話の前に、競合の直し方を決めます。読んだ**版**を持ち、書くときに確かめます。

- 版は、これまでに書かれたイベントの数とする
- 書くときは、読んだ版を添える。現在の版と違えば、書かずに断る

イベントストアの型に、版を足します。

```flix
    pub type alias Versioned = { events = List[ToDoListEvent], version = Int32 }
```

```flix
    pub type alias EventStore[ef: Eff] = {
        load = Unit -> Result[ZettaiError, Versioned] \ ef,
        append = (Int32, List[ToDoListEvent]) -> Result[ZettaiError, Unit] \ ef
    }
```

断る理由は、失敗の型に足します。

```flix
        case VersionConflict(Int32, Int32)
```

ケースを足すと、第 7 章と同じく、コンパイラが `Http.statusOf` の更新漏れを指摘しました。競合は、リクエストの内容ではなく状態の食い違いなので、409 に写しました。

```flix
            case ZettaiError.VersionConflict(_, _)      => 409
```

インメモリの実装は、版が違えば書きません。

```flix
            append = (expected, events) -> {
                let current = Ref.get(history);
                let actual = List.length(current);
                if (expected != actual) {
                    Err(ZettaiError.VersionConflict(expected, actual))
                } else {
                    Ref.put(current ::: events, history);
                    Ok(())
                }
            }
```

PostgreSQL では、チェックだけに頼りません。版の列に**一意制約**を付けます。チェックと書き込みの間にも、別の書き込みが入りうるからです。

```sql
create table if not exists ${table} (seq bigserial primary key, version integer not null unique, kind text not null, user_name text not null, list_name text not null, description text not null, status text not null)
```

制約があれば、同じ版を 2 つのトランザクションが書こうとしたとき、後の書き込みは失敗します。さきほどの手順で確かめます。

```flix
    @Test
    def aUniqueVersionMakesTheSecondStaleWriterFail(): Unit \ {Assert, IO} = {
        let config = PostgresStore.configFromEnv();
        let table = PostgresStore.freshTableName();
        let _ = PostgresStore.execute(config, "create table ${table} (version integer not null unique)");
        let outcome = try {
            Ok(bothReadThenBothWrite(config, table))
        } catch {
            case ex: SQLException => Err(ex.getSQLState())
        };
        Assert.assertEq(expected = Err("23505"), outcome);
        let _ = PostgresStore.dropTable(config, table);
        ()
    }
```

`23505` は、一意制約違反を表す SQLState です。これを、失敗の型に写します。

```flix
    pub def conflictOrFailure(error: SQLException, expected: Int32): ZettaiError \ IO =
        if (error.getSQLState() == "23505")
            ZettaiError.VersionConflict(expected, expected + 1)
        else
            failure(error)
```

`getSQLState()` は `IO` でした。前章で `getMessage()` が `IO` にならなかったことを書きましたが、同じ例外の別のメソッドで効果が違います。Java の呼び出しの効果は、一律ではありません。

### どのストアも守る契約

インメモリと PostgreSQL が、同じ契約を守ることを、1 つのテストで確かめます。

```flix
    def contract(store: EventStore[ef]): Unit \ {Assert, ef} = {
        let empty = store#load();
        Assert.assertEq(expected = Ok(0), Result.map(v -> v#version, empty));
        // 2 つのコマンドが、どちらも空の履歴を読んだ
        let first = store#append(0, created() :: Nil);
        let stale = store#append(0, created() :: Nil);
        Assert.assertEq(expected = Ok(()), first);
        Assert.assertEq(expected = Err(ZettaiError.VersionConflict(0, 1)), stale);
        // 断られた書き込みは、何も残さない
        Assert.assertEq(expected = Ok(created() :: Nil), Result.map(v -> v#events, store#load()));
        Assert.assertEq(expected = Ok(1), Result.map(v -> v#version, store#load()));
        // 読み直した版なら書ける。版はイベントの数だけ進む
        Assert.assertEq(expected = Ok(()), store#append(1, added("a") :: added("b") :: Nil));
        Assert.assertEq(expected = Ok(3), Result.map(v -> v#version, store#load()))
    }
```

契約は、効果 `ef` について多相です。インメモリ（領域の効果）でも、PostgreSQL（`IO`）でも、同じ関数に渡せます。

### 文脈を効果として宣言する

コマンドの処理は、読む、判断する、書くの 3 段でした。必要な文脈を、効果として宣言します。

```flix
    pub eff EventLog {
        def load(): Result[ZettaiError, Versioned]
        def append(expected: Int32, events: List[ToDoListEvent]): Result[ZettaiError, Unit]
    }
```

処理は、この効果の操作を呼ぶだけです。ストアも、接続も、引数に現れません。

```flix
    pub def process(command: Commands.ToDoListCommand): Result[ZettaiError, Unit] \ EventLog =
        forM (
            read   <- EventLog.load();
            events <- Commands.handle(command, Replay.replayFrom(Events.empty(), read#events));
            _      <- EventLog.append(read#version, events)
        ) yield ()
```

シグネチャの `\ EventLog` が、「この処理はイベントの記録先を必要とする」と宣言しています。`ContextReader` が型で守ろうとしたことを、効果が型で守ります。

実装は、ハンドラで与えます。既存のイベントストアを、そのまま文脈にするハンドラです。

```flix
    pub def withStore(store: EventStore[r], f: Unit -> a \ ef): a \ (ef - EventLog) + r =
        run {
            f()
        } with handler EventLog {
            def load(resume) = resume(store#load())
            def append(expected, events, resume) = resume(store#append(expected, events))
        }
```

引数で渡す第 6 章の `CommandHub` と、効果で渡す `Process` が、同じ結果を返すことを、コマンド列の全パターンで確かめます。5 種類のコマンドを長さ 3 まで並べた 156 通りです。

```flix
    @Test
    def bothWaysOfCarryingTheContextGiveTheSameResults(): Unit \ Assert =
        List.forEach(sequence -> Assert.assertEq(expected = byHub(sequence), byHandler(sequence)), allSequences())
```

## トランザクションの境界を決める

境界は、**コマンド 1 つの処理**に置きます。コマンドは業務上の 1 つの意図だからです。

PostgreSQL の実装では、境界は 1 か所だけです。1 つの接続を開き、その接続を使うハンドラを装着して、処理が成功したときだけコミットします。

```flix
    pub def transactional(config: Config, table: String, f: Unit -> Result[ZettaiError, a] \ Process.EventLog): Result[ZettaiError, a] \ IO =
        withConnection(config, conn -> {
            conn.setAutoCommit(false);
            let result = run {
                f()
            } with handler Process.EventLog {
                def load(resume) = resume(readEvents(conn, table))
                def append(expected, events, resume) = resume(insertEvents(conn, table, expected, events))
            };
            match result {
                case Ok(_)  => conn.commit()
                case Err(_) => conn.rollback()
            };
            result
        })
```

接続は、ハンドラが閉じ込めています。`process` は、接続を知りません。それでも、`process` の中の「読む」と「書く」は、同じ接続、同じトランザクションの中で動きます。

これが、`ContextReader` に当たる働きです。**文脈は、引数ではなく、ハンドラが装着された範囲に存在します。**

`readEvents` と `insertEvents` は、接続を引数に取ります。開閉とコミットは、呼び出し側が決めます。

```flix
    def readEvents(conn: Connection, table: String): Result[ZettaiError, Versioned] \ IO =
```

```flix
    def insertEvents(conn: Connection, table: String, expected: Int32, events: List[ToDoListEvent]): Result[ZettaiError, Unit] \ IO =
```

コマンドを 1 つずつ処理する通常の `append` は、1 回の書き込みを 1 つのトランザクションにします。

```flix
    def append(config: Config, table: String, expected: Int32, events: List[ToDoListEvent]): Result[ZettaiError, Unit] \ IO =
        withConnection(config, conn -> {
            conn.setAutoCommit(false);
            let result = insertEvents(conn, table, expected, events);
            match result {
                case Ok(_)  => conn.commit()
                case Err(_) => conn.rollback()
            };
            result
        })
```

### 中のコマンドが 1 つでも拒否されたら

`transactional` の価値は、**複数のコマンドを 1 つの単位にできる**ことです。2 つのコマンドを 1 つのトランザクションに入れて、2 つ目が拒否されると、1 つ目の書き込みも取り消されます。

```flix
    @Test
    def aRejectedCommandRollsBackTheCommandsBeforeItInTheSameTransaction(): Unit \ {Assert, IO} = {
        let config = PostgresStore.configFromEnv();
        let table = PostgresStore.freshTableName();
        let _ = PostgresStore.open(config, table);
        let outcome = PostgresStore.transactional(config, table, () -> forM (
            _ <- Process.process(CreateToDoList(uberto(), book()));
            _ <- Process.process(CreateToDoList(uberto(), book()))
        ) yield ());
        Assert.assertEq(expected = Err(ZettaiError.ListAlreadyExists(uberto(), book())), outcome);
        match PostgresStore.open(config, table) {
            case Ok(store) => Assert.assertEq(expected = Ok(0), Result.map(read -> read#version, store#load()))
            case Err(e)    => Assert.assertEq(expected = "opened", ToString.toString(e))
        };
        let _ = PostgresStore.dropTable(config, table);
        ()
    }
```

1 つ目の `CreateToDoList` は成功していました。それでも、全体が `Err` になったので、データベースには何も残りません。

このテストが本当にロールバックを確かめているのか、`transactional` を「失敗でもコミットする」に書き換えて確かめました。このテストが落ちます。元に戻すと、すべて通ります。

## 設計を 2 回間違えた

この章で、設計の見立てを 2 回直しました。

**1 回目: 「トランザクションに入れれば競合は防げる」。** 最初に思いついた直し方は、読む、判断する、書くを 1 つのトランザクションに入れることでした。それで足りるかを、手順を明示して再現したところ、足りませんでした。READ COMMITTED では、2 つのトランザクションがどちらも古い状態を読んだまま、両方コミットできます。版と一意制約が必要でした。

**2 回目: 「競合には、現在の版を添えて返せる」。** `VersionConflict(expected, actual)` は、読んだ版と現在の版を持つ設計でした。インメモリと、書く前の版の確認では、現在の版が分かります。ところが、一意制約に違反した場合は、違反を検出した時点で、そのトランザクションはもう使えません。現在の版を、その場で問い合わせられないのです。

直し方は、分からないものを分からないまま返すことでした。一意制約の違反では、現在の版を `expected + 1`（少なくともここまで進んでいる）として返し、その旨をコメントに書きました。違反の SQLState から失敗の型への写像は、例外を直接作って、テストで確かめています。

```flix
    @Test
    def aUniqueViolationBecomesAVersionConflict(): Unit \ {Assert, IO} = {
        let violation = new SQLException("duplicate key", "23505");
        let other = new SQLException("syntax error", "42601");
        Assert.assertEq(expected = Errors.ZettaiError.VersionConflict(3, 4), PostgresStore.conflictOrFailure(violation, 3));
        Assert.assertEq(expected = Errors.ZettaiError.StorageFailure("syntax error"), PostgresStore.conflictOrFailure(other, 3))
    }
```

一意制約が違反を起こすことは、2 つの接続の手順を明示して、同じ制約を持つ素のテーブルで確かめています。ただし、`PostgresStore` の `append` を通した実際の競合から `VersionConflict` が返るまでの経路は、1 つのテストでは確かめていません。確かめているのは、版の確認で断る経路（契約のテスト）と、SQLState の写像（上のテスト）を別々に、までです。

**公開後に、この通しを確かめました。** 接続 A が版 1 を挿入してコミットせず、別スレッド B が `append(0, …)` を呼ぶと、B は版の確認を通過して、挿入で A のロック待ちになります。B が終わっていないことを確かめてから A がコミットすると、B は一意制約違反（`23505`）になり、`VersionConflict(0, 1)` が返りました。写像を壊すと、同じテストが `StorageFailure` で落ちます（`TestRealConflict`）。

**もう 1 つ、設計が変わりかけた点。** 追記の関数は、最初は自分でコミットまでしていました。`transactional` から同じ関数を使おうとして、気づきました。中でコミットされると、後続のコマンドが失敗しても取り消せません。追記は「書く」だけにして、コミットは呼び出し側が決める形に分けました。

## データベースに対する射影のクエリ

読み取り側は、第 8 章の `QueryHub` のままで、PostgreSQL のストアの上でも動きます。

```flix
    @Test
    def theQuerySideReadsRowsFromEventsStoredInPostgres(): Unit \ {Assert, IO} = {
        let config = PostgresStore.configFromEnv();
        let table = PostgresStore.freshTableName();
        match PostgresStore.open(config, table) {
            case Err(e)    => Assert.assertEq(expected = "opened", ToString.toString(e))
            case Ok(store) => {
                let commandHub = CommandHub.commandHub(store);
                let queryHub = QueryHub.queryHub(store);
                List.forEach(c -> { let _ = commandHub#execute(c); () }, List#{
                    CreateToDoList(uberto(), book()),
                    AddToDoItem(uberto(), book(), Domain.todo("a")),
                    AddToDoItem(uberto(), book(), Domain.todo("b")),
                    ChangeItemStatus(uberto(), book(), "a", ToDoStatus.Done)
                });
                Assert.assertEq(
                    expected = Ok(List#{ToDoListRow.ToDoListRow(book(), 2, 1)}),
                    queryHub#listRows(uberto())
                )
            }
        };
        let _ = PostgresStore.dropTable(config, table);
        ()
    }
```

ただし、この `QueryHub` は、呼ぶたびに全イベントを読み、メモリの中で畳み込みます。イベントが増えると遅くなります。データベース側で集計する（`GROUP BY` などで行を直接作る）道もありますが、この章では作りませんでした。畳み込みの結果を保存する仕組みと合わせて、必要になったときに考えます。

## イベントソーシングによるドメインのモデリング

この章で、型を変えた波及を数えました。第 7 章、第 9 章と同じ測り方です。

| 種類 | ファイル |
| :--- | :--- |
| src（版を足したため） | `EventStore`、`CommandHub`、`QueryHub`、`PostgresStore` の 4 ファイル |
| src（`VersionConflict` を足したため） | `Errors`、`Http` の 2 ファイル |
| test（版を足したため） | `TestCommandHub`、`TestCommandInvariants`、`TestPostgresStore`、`Routes` の 4 ファイル |

変わらなかったのは、ドメイン、イベント、コマンド、遷移表、射影、HTTP の `route`、受け入れテストのシナリオです。**イベントソーシングでドメインをモデリングしたことの効果は、ここで見えます。** 保存の仕組みが、版の導入という大きな変更を受けても、ドメインの層は触れずに済みました。

## まとめ

- 「読む、判断する、書く」の間に別の書き込みが入る競合を、手順を明示して再現しました。
- トランザクションだけでは足りないことを確かめ、版つきの追記と一意制約で守りました。
- 競合は `VersionConflict`（HTTP 409）にしました。ケースを足すと、コンパイラが写像の更新漏れを指摘しました。
- 文脈を効果 `EventLog` として宣言し、ハンドラで与えました。引数で渡す版と、効果で渡す版が、156 通りのコマンド列で同じ結果を返しました。
- 1 つの接続とトランザクションを持つハンドラ `transactional` で、複数のコマンドを 1 つの単位にしました。中の 1 つが拒否されると、全体を取り消します。
- 設計を 2 回直しました（トランザクションで足りるという見立て、競合に現在の版を添えられるという見立て）。
- `flix test` は 101 本が通ります。

つまずきとして、`getSQLState()` は `IO` でした。前章で `getMessage()` が `IO` にならなかったことと並べると、同じ例外のメソッドでも効果の扱いが違います。理由は確かめていません。

次章では、リスト名の変更を作ります。入力の誤りを最初の 1 つで止めず、すべてまとめて知らせるために、`Result` では足りない場面が出てきます。

## この章で書いたコード

- 文脈の効果とハンドラ: `apps/flix/zettai/src/Process.flix`
- 版つきのイベントストア: `apps/flix/zettai/src/EventStore.flix`
- PostgreSQL のストアとトランザクション: `apps/flix/zettai/src/PostgresStore.flix`
- コマンドの入口: `apps/flix/zettai/src/CommandHub.flix`
- テスト: `apps/flix/zettai/test/TestVersionedStore.flix`、`TestTransactionIsolation.flix`、`TestProcess.flix`、`TestQueryOnDatabase.flix`

## 参照

- Uberto Barbini『From Objects to Functions』第 10 章
- [Flix 公式ドキュメント](https://doc.flix.dev/)
- [PostgreSQL: Transaction Isolation](https://www.postgresql.org/docs/current/transaction-iso.html)
