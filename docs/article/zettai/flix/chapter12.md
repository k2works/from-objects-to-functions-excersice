---
type: Article
title: "第 12 章 監視と関数型 JSON"
description: "Zettai 連載 Flix 版の第 12 章。ログは操作を包むデコレータが標準の Logger 効果で書き、載せるものを型で決める。標準に無い JSON は木と変換を自作し、境界値のテストで見つかったバグを直す。dimap の法則と、双方向の変換には imap しか作れないことを確かめる。"
tags: [article, zettai, flix, logging, json, profunctor, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-03T09:00:00Z }
---

# 第 12 章 監視と関数型 JSON

前章で、入力の誤りを型で集めました。この章では、ログを足します。ただし、ドメインにもアプリケーションにも、ログのための行は 1 行も足しません。

Flix 版には、ログについて、他の対象に無い下地があります。**ログが効果として型に現れる**ことです。

## アプリケーションを監視する

### 知りたいことと、載せてはならないこと

運用で知りたいのは、次のことです。

- どの操作が、どう終わったか（成功したか、どの理由で拒否されたか）
- どれくらい時間がかかったか
- どの利用者の、どのリストで起きたか

載せてはならないものもあります。

| 載せない | 理由 |
| :--- | :--- |
| 項目の説明 | 利用者が書いた内容そのもの |
| イベントの中身 | 同上 |
| 失敗の理由の文字列 | 接続先やパスワードなどの内部情報を含みうる |
| HTTP の経路・クエリ・本文 | 入力そのもの |

ログを足す方法は、大きく 2 つあります。操作の中に `Logger.info(...)` を書き足すか、操作を包むかです。前者は、ドメインの純粋な関数に副作用を入れます。後者を選びます。

### ドメインにログを足せない

Flix の標準には `Logger` という効果があります。`Logger.info` などを呼ぶと、その関数の型に `Logger` が現れます。効果の注釈が無い関数に足すと、コンパイルが拒否します。

```bash
>> Unexpected effect 'Logger' in {} function.
```

第 1 章で `println` が拒否されたのと同じ守り方が、ログにも効きます。**ドメインの関数にログを足さないという規律を、コンパイラが肩代わりします。**

### 包む対象

`ToDoListHub`、`CommandHub`、`QueryHub`、`EventStore` は、どれも関数値を持つレコードです。関数を 1 つずつ包んだ新しいレコードを返せます。

| 包むもの | 載せる種類 |
| :--- | :--- |
| 読み取りの入口 `ToDoListHub` | `hub` / `getList` |
| コマンドの入口 `CommandHub` | `command` / コマンドの種類 |
| イベントの保管場所 `EventStore` | `store` / `load`・`append` |
| HTTP の要求 | `http` / メソッド |

決定は [ADR-048](../../adr/ADR-048-flix-logger-effect-and-own-json.md) に記録しました。標準の `Logger` 効果を借り、JSON は自作します。Maven のログや JSON のライブラリは、**試していません。**

## 構造化されたロギング

### 載せるものを、型で決める

ログの 1 行は、次の型です。

```flix
    pub enum LogRecord(String, String, String, Option[String], Option[String], Int32) with Eq, ToString
```

種類、名前、結果、利用者、リスト、所要時間のミリ秒の 6 つです。自由な `Map` も、任意の文字列を足す場所も、持たせていません。**項目の説明も、失敗の理由も、この型に置き場所がありません。** 載せないものは、書かない規律ではなく、書けないことで守られます。

テストは、出力に現れるキーが固定の 6 つだけであることを確かめます。

```flix
    @Test
    def thereIsNoFieldForFreeFormContent(): Unit \ Assert = {
        // 項目の説明や理由を入れる場所が、型に無い。出力に現れるキーは固定の 6 つだけ。
        let keys = match Logs.toJson(Logs.LogRecord.LogRecord("a", "b", "c", None, None, 0)) {
            case JObject(kvs) => List.map(kv -> match kv { case (k, _) => k }, kvs)
            case _            => Nil
        };
        Assert.assertEq(expected = List#{"kind", "name", "outcome", "user", "list", "durationMs"}, keys)
    }
```

利用者名とリスト名は載せます。障害の追跡に必要な識別子だからです。ただし、利用者が自分で書く内容で、個人を特定しうるため、ログの保管とアクセスの管理は、運用で守る前提です。

### 操作を包む

操作を実行して、結果にかかわらず 1 行を書く関数が、全体の中心です。

```flix
    pub def observe(clock: Unit -> Int64 \ ec, kind: String, name: String, user: Option[String], list: Option[String],
                    operation: Unit -> Result[ZettaiError, a] \ ef): Result[ZettaiError, a] \ ec + ef + Logger = {
        let startedAt = clock();
        let result = operation();
        let finishedAt = clock();
```

時計は、引数で渡します。標準に時計が見つからなかったためです。テストでは、呼ぶたびに決まった量だけ進む時計を渡すので、所要時間を決まった値にできます。

結果は、そのまま返します。成功は `ok`、失敗は**種類の名前**だけです。

```flix
    pub def category(error: ZettaiError): String =
        match error {
            case ZettaiError.ListNotFound(_, _)         => "ListNotFound"
            case ZettaiError.ListAlreadyExists(_, _)    => "ListAlreadyExists"
            case ZettaiError.ItemNotFound(_, _, _)      => "ItemNotFound"
            case ZettaiError.InvalidTransition(_, _, _) => "InvalidTransition"
            case ZettaiError.StorageFailure(_)          => "StorageFailure"
            case ZettaiError.VersionConflict(_, _)      => "VersionConflict"
            case ZettaiError.InvalidInput(_)            => "InvalidInput"
        }
```

失敗の種類を足すと、第 7 章以来おなじみの更新漏れを、この `match` がコンパイルで指摘します。

読み取りの入口を包みます。

```flix
    pub def observeHub(clock: Unit -> Int64 \ ec, hub: ToDoListHub[ef]): ToDoListHub[ec + ef + Logger] = {
        getList = (user, name) ->
            observe(clock, "hub", "getList", Some(userName(user)), Some(listName(name)), () -> hub#getList(user, name))
    }
```

戻り値の型 `ToDoListHub[ec + ef + Logger]` が、「時計の効果と、元の効果と、`Logger`」を持つことを表します。ログを足したことが、型に出ます。

コマンドの入口は、コマンドの種類・利用者・リストだけを載せます。

```flix
    /// コマンドの入口を包む。コマンドの種類・利用者・リストだけを載せる。項目の説明は載せない。
    pub def observeCommandHub(clock: Unit -> Int64 \ ec, hub: CommandHub[ef]): CommandHub[ec + ef + Logger] = {
        execute = command -> observe(clock, "command", commandName(command),
            Some(commandUser(command)), Some(commandList(command)), () -> hub#execute(command)),
```

項目の説明に `secret item` を入れたコマンドを流しても、どの行にも `secret` が現れないことを、テストで確かめます。

```flix
        // 項目の説明は、どの行にも現れない
        Assert.assertFalse(List.exists(line -> String.contains(substr = "secret", line), lines))
```

### ログの出力先を、テストが握る

テストでは、`Logger` 効果を、自前のハンドラで受けます。行を領域の中の `Ref` に集めるだけです。

```flix
    def collect(rc: Region[r], f: Unit -> a \ (Logger + r)): (a, List[String]) \ r = {
        let lines = Ref.fresh(rc, Nil);
        let result = run {
            f()
        } with handler Logger {
            def log(_severity, message, resume) = {
                Ref.put(Ref.get(lines) ::: (ToString.toString(message) :: Nil), lines);
                resume(())
            }
        };
        (result, Ref.get(lines))
    }
```

本番は、標準の `Logger.runWithIO` に差し替えるだけです。標準出力に、次のような行が出ました。

```bash
[Info] {"kind":"http","name":"GET","outcome":"2xx","user":null,"list":null,"durationMs":0}
[Info] {"kind":"http","name":"GET","outcome":"4xx","user":null,"list":null,"durationMs":0}
```

## JSON を関数型にする

### 文字列を手で組み立てない

ログの 1 行を、JSON にします。`"{\"kind\":" + kind + ...` と文字列を連結すると、引用符のエスケープを忘れた瞬間に、壊れた行ができます。

Flix の標準には、JSON が見つかりませんでした。`Json.parse` も `Json.Null` も、未定義です。そこで、JSON を**木**として持ちます。

```flix
    pub enum JsonTree with Eq, ToString {
        case JNull
        case JBool(Bool)
        case JInt(Int32)
        case JString(String)
        case JArray(List[JsonTree])
        case JObject(List[(String, JsonTree)])
    }
```

つまずきとして、最初は型の名前を `Json` にしました。モジュールの名前と同じ型は公開できないと拒否されたので、`JsonTree` にしました。

### 木と文字列の双方向変換

木から文字列への書き出しは、引用符、バックスラッシュ、改行などをエスケープします。

```flix
            case _    =>
                let code = Character.hashCode(c);
                if (code < 32) unicodeEscape(code) else ToString.toString(c)
```

その他の制御文字は、`\u00XX` の形にします。文字のコードを調べる関数が標準に見つからなかったので、Java の `Character.hashCode` を呼びました。第 9 章の観察どおり、この呼び出しは `IO` になりませんでした。

```flix
    pub def render(json: JsonTree): String =
        match json {
            case JsonTree.JNull        => "null"
            case JsonTree.JBool(true)  => "true"
            case JsonTree.JBool(false) => "false"
            case JsonTree.JInt(n)      => Int32.toString(n)
            case JsonTree.JString(s)   => renderString(s)
            case JsonTree.JArray(xs)   => "[" + String.intercalate(",", List.map(render, xs)) + "]"
            case JsonTree.JObject(kvs) =>
                "{" + String.intercalate(",", List.map(kv -> match kv { case (k, v) => renderString(k) + ":" + render(v) }, kvs)) + "}"
```

逆向きの読み込みも自作しました。JSON でない文字列は、例外ではなく失敗の値になります。

```flix
    @Test
    def rejectsTextThatIsNotJson(): Unit \ Assert = {
        List.forEach(text -> match Json.parse(text) {
            case Err(_) => ()
            case Ok(_)  => Assert.assertEq(expected = "rejected: ${text}", "accepted: ${text}")
        }, List#{"", "{", "[1,", "{\"a\"}", "tru", "\"unterminated", "1 2", "[1 2]", "{\"a\":}", "\"\\q\""})
    }
```

書き出して読み込むと、元の木に戻ることを、全パターンで確かめます。葉は `null`、真偽、整数の端の値、特殊な文字を含む文字列など 12 通りで、葉を 0 〜 2 個持つ配列とオブジェクト、その入れ子を合わせて 640 通りです。

```flix
    @Test
    def parsingWhatWasRenderedGivesBackTheSameTree(): Unit \ Assert = {
        List.forEach(tree -> Assert.assertEq(expected = Ok(tree), Json.parse(Json.render(tree))), trees())
    }
```

### 境界値で見つかったバグ

この往復のテストは、最初は 454 通りで、整数の葉に `0` と `-12` しか入れていませんでした。あとで、コンバータの往復のテストに `Int32` の最小値を入れたとき、**自作のパーサが落ちました。**

```bash
Assertion failed: expected `Ok(-2147483648)`, but got `Err(number out of range)`
```

原因は、桁を先に `Int32` として読んでから符号を付けていたことでした。`2147483648` は `Int32` に入らないので、最小値だけが範囲外になります。符号つきの文字列として読むように直しました。

```flix
            // 符号つきで読む。桁だけを先に読むと、最小値（-2147483648）が範囲外になる
            let text = (if (negative) "-" else "") + String.intercalate("", List.map(ToString.toString, digits));
```

あわせて、JSON のテストの葉に `Int32` の最小値と最大値を足しました。木の数は 640 通りになりました。**自作するなら、境界値は最初から入れる**という、ADR-048 の約束にも書いてあります。

ログの 1 行も、木との双方向変換を持ちます。読めない行は、失敗の値になります。

```flix
    @Test
    def aFieldOfTheWrongTypeIsRejected(): Unit \ Assert =
        Assert.assertEq(
            expected = Err("field durationMs is not an integer"),
            Logs.fromJson(JObject(
                ("kind", JString("hub")) :: ("name", JString("x")) :: ("outcome", JString("ok"))
                :: ("user", JNull) :: ("list", JNull) :: ("durationMs", JString("12")) :: Nil))
        )
```

## プロファンクタとの出会い

### 入力の側と出力の側

ここまで、ログの包み方を何度も書きました。関数を、入力と出力の両側から包む操作には、名前があります。**入力を読み替え、出力を書き換える**操作です。

```flix
    pub def dimap(pre: a2 -> a, post: b -> b2, f: a -> b): a2 -> b2 =
        x -> post(f(pre(x)))
```

`pre` は、新しい入力から元の入力への写像で、逆向きです（反変）。`post` は、元の出力から新しい出力への写像で、順向きです（共変）。この両方を持つ構造を、プロファンクタと呼びます。

文字列を受け取り、文字列を返す関数に見せかける例です。

```flix
    @Test
    def theInputIsMappedBackwardsAndTheOutputForwards(): Unit \ Assert = {
        // 文字列を受け取り、文字列を返す関数に見せかける。入力は読み替え、出力は書き換える
        let length = Profunctor.dimap(s -> String.length(s), n -> "length=${n}", n -> n * 2);
        Assert.assertEq(expected = "length=8", length("abcd"))
    }
```

何もしない写像を渡せば元に戻り、2 回の `dimap` は、写像を合成した 1 回と同じになります。小さな定義域のすべての点で確かめます。

```flix
    @Test
    def dimapWithIdentitiesChangesNothing(): Unit \ Assert =
        List.forEach(f ->
            List.forEach(x -> Assert.assertEq(expected = f(x), Profunctor.dimap(a -> a, b -> b, f)(x)), domain()),
            base())
```

入力の写像 4 つ × 4 つ、出力の写像 4 つ × 4 つ、元の関数 3 つ、定義域の点 8 つの組み合わせを、すべて確かめます。

### 双方向の束には、一般の dimap が無い

`dimap` は、関数には作れます。では、木との双方向変換には作れるでしょうか。

```flix
    pub type alias Converter[a] = {
        encode = a -> JsonTree,
        decode = JsonTree -> Result[String, a]
    }
```

型 `a` は、`encode` の入力にも、`decode` の出力にも現れます。つまり、**反変の位置と共変の位置の両方に現れます。** 入力だけを読み替える写像、出力だけを書き換える写像では、どちらかの側が合わなくなります。型を変えるには、行きと戻りの**両方向**の写像が要ります。

```flix
    pub def imap(toB: a -> b, toA: b -> a, c: Converter[a]): Converter[b] = {
        encode = value -> c#encode(toA(value)),
        decode = json -> Result.map(toB, c#decode(json))
    }
```

行きと戻りの写像を渡せば、型を変えられます。

```flix
    @Test
    def aConverterNeedsBothDirectionsToChangeItsType(): Unit \ Assert = {
        // 長さ 3 の文字列を包む型。書く向きと読む向きの、2 つの写像を渡す
        let lengthOf = Converter.imap(s -> String.length(s), n -> String.repeat(n, "x"), stringConverter());
        Assert.assertEq(expected = JString("xxx"), lengthOf#encode(3));
        Assert.assertEq(expected = Ok(3), lengthOf#decode(JString("xxx")))
    }
```

関数には `dimap`、双方向の束には `imap`。**型の位置が、使える道具を決めます。**

## データベース呼び出しのロギング

### 保管場所を包む

データベースへの呼び出しも、保管場所を包めば記録できます。PostgreSQL の実装にも、インメモリの実装にも、同じ包み方が効きます。

```flix
    pub def observeStore(clock: Unit -> Int64 \ ec, store: EventStore[ef]): EventStore[ec + ef + Logger] = {
        load = () -> observe(clock, "store", "load", None, None, () -> store#load()),
        append = (expected, events) -> observe(clock, "store", "append", None, None, () -> store#append(expected, events))
    }
```

失敗の理由に、漏らしたくない値を入れた、わざと失敗するストアで確かめます。

```flix
    def failingStore(): EventStore.EventStore[{}] = {
        load = () -> Err(ZettaiError.StorageFailure("password=hunter2 host=db.internal")),
        append = (_expected, _events) -> Err(ZettaiError.StorageFailure("password=hunter2 host=db.internal"))
    }
```

行には、`StorageFailure` という種類だけが載り、理由は載りません。

```flix
        Assert.assertFalse(List.exists(line -> String.contains(substr = "hunter2", line) or String.contains(substr = "db.internal", line), lines))
```

### request も記録する

HTTP の要求も、包みます。メソッドと、状態コードの区分（`2xx`、`4xx`）だけを載せます。

```flix
    def statusClass(status: Int32): String = Int32.toString(status / 100) + "xx"
```

経路には利用者名とリスト名が、本文には新しい名前が入ります。どちらも利用者の入力なので、載せません。

```flix
        // 経路にも本文にも利用者の入力が入る。行には、どちらも現れない
        Assert.assertFalse(List.exists(line -> String.contains(substr = "secret", line), lines))
```

### 組み立てる

実サーバーでは、サーバーのコールバックが、`Logger` を処理するハンドラの外で動きます。第 2 章で見た、効果の保証が Java のコールバックで切れる場所と同じです。**`Logger` の処理は、コールバックの内側に置きます。**

### 外側に置くとどうなるか（公開後に確かめたこと）

ハンドラを `JdkServer.start` を呼ぶ側（コールバックの外）に置くと、**コンパイルは通り、型検査は何も警告しません。** 実行すると、コールバックの中で `Logger` を呼んだところで処理が止まり、応答が返らずに接続が切れます。クライアントには `Unexpected end of file from server` が届きます。`Logger` を呼んだ後ろの処理は実行されず、コールバックの中の `try` / `catch` でも例外は捕まりませんでした（`HttpURLConnection` が GET を自動で再送するため、コールバックは 2 回呼ばれます）。仕組みまでは確かめていません。

この落とし穴は、回帰テストとして残しました。

```flix
                let answered = try {
                    let (status, _) = HttpTestClient.get(port, "/todo/uberto/book");
                    Ok(status)
                } catch {
                    case ex: IOException => Err("no response: ${ex.getMessage()}")
                };
```

**第 2 章で見た「効果の保証が Java のコールバックで切れる」ことの、具体的な形です。** 切れた先で、エラーにならず、黙って応答が消えます。

```flix
            let app = Logging.observeApp(now, request -> Http.application(hub, commands#execute, request));
            // サーバーのコールバックは、ハンドラの外で動く。Logger は、コールバックの内側で処理する
            let server = JdkServer.start(0, request -> run { app(request) } with Logger.runWithIO);
```

時計は、`System.currentTimeMillis()` を呼ぶ関数です。この呼び出しは `IO` で、ログの所要時間が、本物の時刻から作られます。

### 置き場所を間違えた

この章で、置き場所を 4 回間違えました。

**1. 時計の効果を、包む対象の効果と同じにした。** 最初の `observeHub` は、時計と Hub に同じ効果 `ef` を要求していました。テストの時計は領域の効果を持ち、インメモリの Hub は効果を持たないので、型が合いませんでした。時計の効果 `ec` を別の型変数にして、結果の型を `ec + ef + Logger` にしました。

**2. 領域の外で、結果を分解した。** ログを集める領域の外で、`let (_, lines) = region ... { ... }` と書くと、型が合いませんでした。領域の内側で取り出して返す補助関数 `linesOf` を作りました。

**3. 無名の関数を渡した。** `Request -> Response` を期待する場所に、無名の `request -> { status = 404, ... }` を渡すと、型が合いませんでした。名前の付いた関数 `notFoundApp` にすると通りました。理由は確かめていません。

**4. `current` が内側を 2 回呼んでいた。** コマンドの入口の `current` のログを書いたとき、結果を捨てて、もう一度内側を呼ぶ形にしていました。ストアの読み出しが 2 回記録されるテストを書いて、初めて気づきました。結果をそのまま返す形に直しました。

```flix
        current = () -> observe(clock, "command", "current", None, None, () -> hub#current())
```

4 つ目は、型では見つからず、テストが見つけました。包む操作が結果を変えないこと、内側を余計に呼ばないことは、テストで確かめるしかありません。

## まとめ

- ログは、操作を包むデコレータが、標準の `Logger` 効果で書きます。ドメインの関数にログを足すと、コンパイルが拒否します。
- ログの 1 行は、6 つの欄だけを持つ型です。項目の説明、失敗の理由、経路、本文は、載せる場所が型にありません。
- JSON は標準に無いので、木と変換を自作しました。往復を 640 通りで確かめ、`Int32` の最小値で壊れていたバグを、境界値のテストで見つけて直しました。
- `dimap` の法則と、双方向のコンバータには `imap` しか作れないことを確かめました。
- データベースの呼び出しと HTTP の要求も包み、失敗の理由や入力が行に現れないことを確かめました。`Logger` の処理は、サーバーのコールバックの内側に置きます。
- 置き場所を 4 回間違えました。そのうち 1 つは、テストだけが見つけました。
- `flix test` は 157 本が通ります。

つまずきとして、ライブラリを試していないことを、ADR に正直に書く必要がありました。比べた結果が無いのに、「ライブラリより自作が良い」とは書けません。

次章では、4 つの言語（Kotlin、なでしこ3、Rust、Flix）に Kotlin FP 版の実装方針を加えた、5 つの実装を並べて総括します。

## この章で書いたコード

- ログのデコレータ: `apps/flix/zettai/src/Logging.flix`
- ログの 1 行の型: `apps/flix/zettai/src/Logs.flix`
- JSON の木と変換: `apps/flix/zettai/src/Json.flix`
- プロファンクタとコンバータ: `apps/flix/zettai/src/Profunctor.flix`、`Converter.flix`
- テスト: `apps/flix/zettai/test/TestLogging.flix`、`TestLogRecord.flix`、`TestJson.flix`、`TestProfunctor.flix`、`TestLoggingOverHttp.flix`

## 参照

- Uberto Barbini『From Objects to Functions』第 12 章
- [ADR-048 Flix 版のログは標準の Logger 効果を借り、JSON は自作する](../../adr/ADR-048-flix-logger-effect-and-own-json.md)
- [Flix 公式ドキュメント](https://doc.flix.dev/)
