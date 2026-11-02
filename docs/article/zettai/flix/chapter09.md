---
type: Article
title: "第 9 章 モナドによる安全なデータ永続化"
description: "Zettai 連載 Flix 版の第 9 章。JDBC を Java 相互運用で呼び、イベントを PostgreSQL に書き読みする。SQL の例外を値としての失敗にし、forM と Traversable で Result の連鎖を書く。同じ受け入れシナリオを 3 経路目のデータベースで実行する。"
tags: [article, zettai, flix, postgresql, jdbc, monad, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-03T00:00:00Z }
---

# 第 9 章 モナドによる安全なデータ永続化

前章までのイベントストアは、メモリの中にだけありました。この章では、イベントを PostgreSQL に書き、読み戻します。

データベースの呼び出しは、失敗します。接続できない、SQL が間違っている、読んだ行の意味が分からない。**失敗を例外のまま上に投げず、第 7 章の `Result` に載せる**のが、この章の主題です。

Flix には JDBC がありません。JDBC は Java のものなので、第 2 章の HTTP と同じく Java 相互運用で呼びます。効果の保証が切れる場所が、もう 1 つ増えます。

後の章で書き換わったコードのブロックには、この章を書いた時点のコミットを示してあります。

## 安全に永続化する

永続化で起きうる失敗を、先に数えます。

| 失敗 | 例 |
| :--- | :--- |
| 接続できない | 接続先が停止している、ポートが違う |
| SQL が失敗する | テーブルが無い、制約に違反する |
| 読んだ行が読めない | 知らない種類のイベント、知らない状態の名前 |
| 外から来た値が危ない | テーブル名に SQL が混ざる |

これらすべてを、`ZettaiError` の新しいケースにまとめます。

```flix
        case StorageFailure(String)
```

ケースを足すと、コンパイラが更新漏れを教えてくれます。`Web.Http.statusOf` はすべてのケースを列挙する `match` なので、足した直後にコンパイルが落ちました。

```bash
>> Non-exhaustive match: missing case 'StorageFailure(_)'.
```

保存の失敗は、利用者の誤りではなくサーバー側の問題なので、500 に写しました。

```flix
            case ZettaiError.StorageFailure(_)          => 500
```

## PostgreSQL と結合テスト

データベースを使うテストは、他のテストと性質が違います。外部のプロセスが要り、時間がかかり、前のテストの残りに影響されます。

方針は 3 つです。

- 接続先は環境変数 `ZETTAI_DB_URL` で決める。無ければ、`docker-compose.yml` の `zettai-db`（5432 番）を使う
- テストごとに**専用のテーブル**を作り、終わったら捨てる。共有のテーブルに書くと、テストの順番に結果が左右される
- CI では、GitHub Actions のサービスコンテナで PostgreSQL を起動する

この PC では、5432 番を別のプロジェクトのコンテナが使っていました。そちらには触れず、Zettai 用の使い捨てのコンテナを 55433 番で起動し、環境変数で向けました。

```bash
docker run -d --name zettai-flix-db -p 55433:5432 -e POSTGRES_DB=zettai -e POSTGRES_USER=zettai -e POSTGRES_PASSWORD=zettai postgres:17-alpine
ZETTAI_DB_URL=jdbc:postgresql://localhost:55433/zettai flix test
```

接続先を環境変数にしておくと、こういう衝突に手を入れずに対処できます。

## Flix でデータベースに接続する

JDBC ドライバは、Maven から取得します。`flix.toml` に依存を書くだけです。

```toml
[mvn-dependencies]
"org.postgresql:postgresql" = "42.7.4"
```

最初の `flix test` で、ドライバと、その依存（`checker-qual`）が取得されます。

JDBC の定番は `DriverManager.getConnection` です。ところが、Flix からそのまま呼ぶと失敗しました。

```bash
java.sql.SQLException: No suitable driver found for jdbc:postgresql://localhost:55433/zettai
```

ドライバの jar は取得できているのに、`DriverManager` が見つけられません。Flix が読み込むクラスの経路と、`DriverManager` が探す経路が違うためだと考えられますが、仕組みの確認まではしていません。

回避は、ドライバを直接生成して、接続することでした。

<!-- code-check: ignore この章を書いた時点のコード（コミット 623a592）。第 10 章で EventStore に版を足し、読んだ版を添えて書く形に変えた -->
```flix
    def connect(config: Config): Connection \ IO = {
        let props = new Properties();
        props.setProperty("user", config#user);
        props.setProperty("password", config#password);
        new Driver().connect(config#url, props)
    }
```

接続先の設定は、環境変数から読みます。環境変数が無いときは `null` が返ります。

```flix
    pub def configFromEnv(): Config \ IO = {
        let fromEnv = System.getenv("ZETTAI_DB_URL");
        let url = if (isNull(fromEnv)) "jdbc:postgresql://localhost:5432/zettai" else fromEnv;
        { url = url, user = "zettai", password = "zettai" }
    }
```

`null` は Java の世界のものです。Java から返った値が `null` かもしれないので、使う前に `Objects.isNull` で確かめ、既定の値に切り替えます。

```flix
    def isNull(value: String): Bool =
        Objects.isNull(unchecked_cast(value as Object))
```

### 効果の観察

実装の途中で、気づいたことがあります。`Objects.isNull` と、例外から `getMessage()` を取り出す呼び出しに `\ IO` を付けたところ、「使っていない効果」としてコンパイラに拒否されました。第 2 章で見た `System.currentTimeMillis()` は `IO` と判定されたので、**Java の呼び出しがすべて `IO`** というわけではないようです。なぜ違うのかは、この時点では確かめていませんでした。公式ドキュメントの「Java とのやりとりは常に `IO`」と合わない観察なので、事実として書き留めておきました。理由は、公開後に分かりました（[第 13 章](chapter13.md)）。コンパイラが、Java のクラス・メソッドごとの効果の表を持っていて、表に無い呼び出しは `IO` になるためです。

### 例外を値にする

JDBC は `SQLException` を投げます。接続を開き、処理し、必ず閉じる関数で、例外を `Result` の失敗に変えます。

```flix
    def withConnection(config: Config, f: Connection -> Result[ZettaiError, a] \ IO): Result[ZettaiError, a] \ IO =
        try {
            let conn = connect(config);
            let result = try {
                f(conn)
            } catch {
                case ex: SQLException => Err(failure(ex))
            };
            conn.close();
            result
        } catch {
            case ex: SQLException => Err(failure(ex))
        }
```

内側の `try` は処理の失敗を、外側の `try` は接続の失敗を受け持ちます。内側で受けるので、処理が失敗しても `conn.close()` は必ず実行されます。

```flix
    def failure(error: SQLException): ZettaiError =
        ZettaiError.StorageFailure(error.getMessage())
```

存在しないポートに接続しても、例外ではなく失敗の値が返ることを、テストで確かめます。

```flix
    @Test
    def anUnreachableDatabaseIsAFailureNotAnException(): Unit \ {Assert, IO} = {
        let config = { url = "jdbc:postgresql://localhost:1/zettai", user = "zettai", password = "zettai" };
        match Adapter.Postgres.PostgresStore.open(config, "events_unreachable") {
            case Err(ZettaiError.StorageFailure(_)) => ()
            case _                                  => Assert.assertTrue(false)
        }
    }
```

## データベースを準備する

イベントを入れるテーブルを作ります。1 行が 1 つのイベントで、`seq` が書いた順序を表します。

<!-- code-check: ignore この章を書いた時点のコード（コミット 623a592）。第 10 章で EventStore に版を足し、読んだ版を添えて書く形に変えた -->
```flix
            match execute(config, "create table if not exists ${table} (seq bigserial primary key, kind text not null, user_name text not null, list_name text not null, description text not null, status text not null)") {
```

テーブル名は、文字列の補間で SQL に入れています。プレースホルダ（`?`）はテーブル名には使えないためです。そのままでは SQL インジェクションの入口になるので、**英小文字・数字・下線だけ**を許します。

```flix
    pub def validTableName(name: String): Bool =
        not String.isEmpty(name) and String.forAll(c -> Char.isLowerCase(c) or Char.isDigit(c) or c == '_', name)
```

不正な名前は、データベースに触れる前に拒否します。

```flix
    @Test
    def aTableNameThatIsNotPlainIsRejectedBeforeAnyQuery(): Unit \ {Assert, IO} = {
        let config = Adapter.Postgres.PostgresStore.configFromEnv();
        match Adapter.Postgres.PostgresStore.open(config, "events; drop table users") {
            case Err(ZettaiError.StorageFailure(_)) => ()
            case _                                  => Assert.assertTrue(false)
        }
    }
```

テストごとの専用テーブルの名前は、UUID から作ります。

```flix
    pub def freshTableName(): String \ IO =
        "events_${String.replace(src = "-", dst = "_", UUID.randomUUID().toString())}"
```

終わったら捨てます。

```flix
    def withTable(f: (Adapter.Postgres.PostgresStore.Config, String) -> Unit \ {Assert, IO}): Unit \ {Assert, IO} = {
        let config = Adapter.Postgres.PostgresStore.configFromEnv();
        let table = Adapter.Postgres.PostgresStore.freshTableName();
        f(config, table);
        let _ = Adapter.Postgres.PostgresStore.dropTable(config, table);
        ()
    }
```

この後始末には弱点があります。`f` の中でアサーションが失敗すると、`dropTable` まで進みません。実際、経路の検証のために DB 経路をわざと壊したとき、テーブルが 1 つ残りました。手で捨てました。**失敗したテストが残骸を残す**ことは、`README.md` の既知の制約に書きました。

## 関数型スタイルでリモートデータにアクセスする

第 6 章のイベントストアは、メモリの中では失敗しないので、戻り値に失敗がありませんでした。データベースでは失敗しうるので、型を変えます。

<!-- code-check: ignore この章を書いた時点のコード（コミット 623a592）。第 10 章で EventStore に版を足し、読んだ版を添えて書く形に変えた -->
```flix
    pub type alias EventStore[ef: Eff] = {
        load = Unit -> Result[ZettaiError, List[ToDoListEvent]] \ ef,
        append = List[ToDoListEvent] -> Result[ZettaiError, Unit] \ ef
    }
```

波及を数えました。第 7 章と同じ測り方です。`StorageFailure` を足したための変更（`Errors`、`Http`、`TestErrorMapping`）を除いて、**src が 3 ファイル、test が 3 ファイル**でした。

| ファイル | 変更の内容 |
| :--- | :--- |
| `src/Application.EventStore.flix` | 型の別名と、インメモリの実装 |
| `src/Application.CommandHub.flix` | `forM` で書き直した |
| `src/Application.QueryHub.flix` | 戻り値を `Result` に |
| `test/TestCommandHub.flix` | 履歴の取り出し方 |
| `test/TestCommandInvariants.flix` | 履歴の取り出し方 |
| `test/TestQueryHub.flix` | 期待値を `Ok` に |

変わらなかったのは、ドメイン、イベント、コマンド、遷移表、射影、HTTP の `route` です。`Hub`（読み取り側）の型も、変わりませんでした。

PostgreSQL の実装は、この型をそのまま満たします。効果が `IO` になるだけです。

```flix
    pub def open(config: Config, table: String): Result[ZettaiError, EventStore[IO]] \ IO =
```

書き込みは、1 回の呼び出しの全イベントを 1 つのトランザクションにします。

```flix
            conn.setAutoCommit(false);
```

途中で失敗すればコミットされないまま接続が閉じます。PostgreSQL はコミットされていない変更を破棄するはずですが、この動作はテストしていません。

## モナドの力を探る

第 6 章のコマンドの入口は、「履歴を読む、コマンドを判断する、イベントを書く」の 3 段です。3 つすべてが失敗しうるようになりました。`forM` で書くと、失敗の扱いが一箇所に収まります。

<!-- code-check: ignore この章を書いた時点のコード（コミット 623a592）。第 10 章で EventStore に版を足し、読んだ版を添えて書く形に変えた -->
```flix
        execute = command -> forM (
            history <- store#load();
            events  <- Domain.Commands.handle(command, Domain.Events.Replay.replayFrom(Domain.Events.empty(), history));
            _       <- store#append(events)
        ) yield (),
```

どれかが `Err` を返せば、そこで止まり、後続は実行されません。読み込みに失敗すれば、コマンドの判断も、書き込みも行われません。

```flix
    @Test
    def aFailureStopsTheChainAndSkipsTheRest(): Unit \ Assert = {
        let result = forM (
            a <- Ok(1);
            b <- (Err(failure()): Result[ZettaiError, Int32]);
            c <- (Ok(100): Result[ZettaiError, Int32])
        ) yield a + b + c;
        Assert.assertEq(expected = Err(failure()), result)
    }
```

この連鎖が予測どおりに合成できるのは、`flatMap` がモナド則を満たすからです。第 7 章の `map` の法則と同じく、3 つの法則を全パターンで確かめます。

- 左単位元: 値を `Ok` に包んで `flatMap` するのは、関数を直接呼ぶのと同じ
- 右単位元: `Ok` を返す関数で `flatMap` しても、元の値と同じ
- 結合律: `flatMap` を入れ子にしても、まとめても、結果は同じ

```flix
    def associativity(bind: (Int32 -> Result[ZettaiError, Int32], Result[ZettaiError, Int32]) -> Result[ZettaiError, Int32]): Bool =
        List.forAll(m ->
            List.forAll(f ->
                List.forAll(g -> bind(g, bind(f, m)) == bind(x -> bind(g, f(x)), m), steps()),
                steps()),
            values())
```

標準の `Result.flatMap` は、3 つとも満たします。

```flix
    @Test
    def flatMapObeysTheThreeLaws(): Unit \ Assert = {
        Assert.assertTrue(leftIdentity(Result.flatMap));
        Assert.assertTrue(rightIdentity(Result.flatMap));
        Assert.assertTrue(associativity(Result.flatMap))
    }
```

第 7 章と同じく、法則のテストが壊れた実装を見分けられるかも確かめます。負の値だけ、関数の結果を無視して失敗にしてしまう `bind` を用意しました。

```flix
    def brokenBind(f: Int32 -> Result[ZettaiError, Int32], m: Result[ZettaiError, Int32]): Result[ZettaiError, Int32] =
        match m {
            case Ok(x) => if (x < 0) Err(failure()) else f(x)
            case Err(e) => Err(e)
        }
```

```flix
    @Test
    def theLawTestsTellABrokenBindApart(): Unit \ Assert = {
        Assert.assertFalse(leftIdentity(brokenBind));
        Assert.assertFalse(rightIdentity(brokenBind))
    }
```

左単位元と右単位元の両方が破れます。なお、これらの法則のテストは、書いた時点ですでに通りました。確かめている対象が標準の `Result` で、新しく作った実装ではないためです。

もう 1 つの連鎖は、読んだ行をイベントに戻す部分です。行は複数あり、どれかが読めなければ全体が失敗です。標準の `Traversable.traverse` が、それを 1 行で表します。

<!-- code-check: ignore この章を書いた時点のコード（コミット 623a592）。第 10 章で EventStore に版を足し、読んだ版を添えて書く形に変えた -->
```flix
            case Ok(rows) => Traversable.traverse(Adapter.Postgres.Codec.decode, rows)
```

`Adapter.Postgres.Codec.decode` は、1 行を `Result[ZettaiError, ToDoListEvent]` に変える関数です。`traverse` は、それを全行に当て、すべて成功ならイベントのリスト、1 つでも失敗なら最初の失敗を返します。

## 読み戻す

書いたものが、順序も含めて、そのまま読み戻せることを確かめます。特殊文字を含む項目で確かめます。

<!-- code-check: ignore この章を書いた時点のコード（コミット 623a592）。第 10 章で EventStore に版を足し、読んだ版を添えて書く形に変えた -->
```flix
    @Test
    def eventsComeBackInTheOrderTheyWereAppended(): Unit \ {Assert, IO} =
        withTable((config, table) -> match Adapter.Postgres.PostgresStore.open(config, table) {
            case Err(e)    => Assert.assertEq(expected = "opened", ToString.toString(e))
            case Ok(store) => {
                Assert.assertEq(expected = Ok(()), store#append(history()));
                Assert.assertEq(expected = Ok(history()), store#load())
            }
        })
```

別の接続からも読めることを確かめます。メモリ上の値を返しているだけではないという証拠です。

<!-- code-check: ignore この章を書いた時点のコード（コミット 623a592）。第 10 章で EventStore に版を足し、読んだ版を添えて書く形に変えた -->
```flix
    @Test
    def anotherConnectionReadsWhatWasWritten(): Unit \ {Assert, IO} =
        withTable((config, table) -> match (Adapter.Postgres.PostgresStore.open(config, table), Adapter.Postgres.PostgresStore.open(config, table)) {
            case (Ok(writer), Ok(reader)) => {
                Assert.assertEq(expected = Ok(()), writer#append(List#{ListCreated(uberto(), book())}));
                Assert.assertEq(expected = Ok(List#{ListCreated(uberto(), book())}), reader#load())
            }
            case _ => Assert.assertTrue(false)
        })
```

読めない行が混ざっているときは、例外ではなく失敗の値になります。

<!-- code-check: ignore この章を書いた時点のコード（コミット 623a592）。第 10 章で EventStore に版を足し、読んだ版を添えて書く形に変えた -->
```flix
    @Test
    def aRowThatCannotBeReadIsAFailureNotAnException(): Unit \ {Assert, IO} =
        withTable((config, table) -> match Adapter.Postgres.PostgresStore.open(config, table) {
            case Err(_)    => Assert.assertTrue(false)
            case Ok(store) => {
                let inserted = Adapter.Postgres.PostgresStore.execute(config,
                    "insert into ${table} (kind, user_name, list_name, description, status) values ('Deleted', 'u', 'l', '', '')");
                Assert.assertEq(expected = Ok(()), inserted);
                Assert.assertEq(expected = Err(ZettaiError.StorageFailure("unknown event kind: Deleted")), store#load())
            }
        })
```

## 3 経路目

第 3 章で、受け入れテストの経路を関数値のレコードで差し替える形にしました。経路を足すとき、シナリオのコードは変わらない、というのが [ADR-046](../../adr/ADR-046-flix-acceptance-by-record-of-functions.md) の約束でした。ここで確かめます。

3 経路目は、データベースです。リストの集まりを、それを作ったイベントの列に戻し、PostgreSQL に書いて、読み戻して状態を作ります。

```flix
    def eventsOf(lists: Map[User, List[ToDoList]]): List[ToDoListEvent] =
        List.flatMap(entry -> match entry {
            case (user, userLists) =>
                List.flatMap(list -> match list {
                    case ToDoList.ToDoList(name, items) =>
                        ToDoListEvent.ListCreated(user, name) ::
                            List.map(item -> ToDoListEvent.ItemAdded(user, name, item), items)
                }, userLists)
        }, Map.toList(lists))
```

<!-- code-check: ignore この章を書いた時点のコード（コミット 623a592）。第 10 章で EventStore に版を足し、読んだ版を添えて書く形に変えた -->
```flix
    pub def databaseActions(lists: Map[User, List[ToDoList]]): Result[ZettaiError, ZettaiActions] \ IO = {
        let config = Adapter.Postgres.PostgresStore.configFromEnv();
        let table = Adapter.Postgres.PostgresStore.freshTableName();
        forM (
            store <- Adapter.Postgres.PostgresStore.open(config, table);
            _     <- store#append(eventsOf(lists))
        ) yield {
            name = "database",
```

3 つの経路で同じシナリオを実行します。

```flix
        let database = databaseActions(lists);
        let routes = match database {
            case Ok(actions) => domainActions(lists) :: httpActions(lists) :: actions :: Nil
            case Err(e)      => {
                Assert.assertEq(expected = "the database route opens", ToString.toString(e));
                Nil
            }
        };
```

**変わったのは `Routes` だけです。** `Scenarios.flix` も `TestAcceptance.flix` も 1 文字も変えていません。経路が 2 つから 3 つになり、受け入れテストは 3 本とも 3 経路で通りました。

3 経路目が本当に動いているのか、DB 経路だけを壊して確かめました。読み戻した項目を逆順にすると、失敗メッセージに `database` と出て、落ちた経路が分かります。

```bash
(Assertion failed: expected `(database, Some(ToDoItem(write chapter, Todo) :: ToDoItem(publish book, Todo) :: ToDoItem(read <today> & "FP", Todo) :: Nil))`, but got `(database, Some(ToDoItem(read <today> & "FP", Todo) :: ToDoItem(publish book, Todo) :: ToDoItem(write chapter, Todo) :: Nil))`)
```

元に戻すと、`flix test` は 91 本が通ります。

## まとめ

- 保存の失敗を `ZettaiError.StorageFailure` にし、HTTP への写像の更新漏れを、コンパイラの `Non-exhaustive match` で見つけました。
- JDBC を Java 相互運用で呼びました。`DriverManager` は「No suitable driver」で使えず、ドライバを直接生成して接続しました。
- `SQLException` を `try` / `catch` で受け、`Result` の失敗にしました。接続は、処理が失敗しても必ず閉じます。
- テーブル名の SQL インジェクションを、許す文字を絞って、データベースに触れる前に拒否しました。
- イベントストアの型を `Result` に変えた波及は、src 3、test 3 ファイルでした。
- `forM` と `Traversable.traverse` で、失敗しうる連鎖を書きました。`Result.flatMap` のモナド則を全パターンで確かめ、壊れた `bind` を見分けられることを示しました。
- 同じ受け入れシナリオを 3 経路目のデータベースで実行しました。シナリオのコードは変わりませんでした。
- CI に PostgreSQL のサービスコンテナを足しました。`flix test` は 91 本が通ります。

つまずきとして、次の 3 つがありました。

- `DriverManager` が使えない（上記）
- テストが失敗すると専用のテーブルが残る（後始末の弱点）
- コードを push する前に記事の検査を回さず、CI の記事検査が落ちた。型を `Result` に変えた結果、第 3・6・8 章の旧コードのブロックがずれたためで、各ブロックに時点を明記して直した

最後の 1 つは、ローカルで検査スクリプトを回せば防げたものです。以後は、実装を変えるたびに、記事の検査を先に回します。

次章では、コマンドの処理に文脈を持ち回ります。データベースの接続やトランザクションを、引数ではなく効果として渡せるかを検討します。

## この章で書いたコード

- PostgreSQL のストア: `apps/flix/zettai/src/Adapter.Postgres.PostgresStore.flix`
- コーデック: `apps/flix/zettai/src/Adapter.Postgres.Codec.flix`
- イベントストアの型: `apps/flix/zettai/src/Application.EventStore.flix`
- コマンドの入口: `apps/flix/zettai/src/Application.CommandHub.flix`
- 依存: `apps/flix/zettai/flix.toml`
- テスト: `apps/flix/zettai/test/TestPostgresStore.flix`、`TestCodec.flix`、`TestResultMonad.flix`、`Routes.flix`

## 参照

- Uberto Barbini『From Objects to Functions』第 9 章
- [ADR-046 Flix 版は受け入れテストの経路を関数値のレコードで差し替え、Cucumber を入れない](../../adr/ADR-046-flix-acceptance-by-record-of-functions.md)
- [Flix 公式ドキュメント](https://doc.flix.dev/)
- [PostgreSQL JDBC Driver](https://jdbc.postgresql.org/)
