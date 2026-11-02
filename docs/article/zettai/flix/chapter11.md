---
type: Article
title: "第 11 章 アプリカティブによるデータバリデーション"
description: "Zettai 連載 Flix 版の第 11 章。リスト名の変更を作り、入力の誤りを最初の 1 つで止めずにすべて集める。標準の Validation と forA で誤りを規則の順・入力の順に蓄積し、その性質を全パターンで確かめる。HTTP の応答は、誤りをまとめて返し、入力の値は返さない。"
tags: [article, zettai, flix, validation, applicative, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-03T04:00:00Z }
---

# 第 11 章 アプリカティブによるデータバリデーション

前章で、コマンドの処理に必要な文脈を、効果ハンドラで持ち回りました。この章では、その最初の使い手として、ToDo リストの名前変更を作ります。あわせて、利用者が入力した名前の誤りを、最初の 1 つで止めずに、すべてまとめて知らせます。

後の章で書き換わったコードのブロックには、この章を書いた時点のコミットを示してあります。

## ToDo リストの名前変更

### 名前変更は、旧い名前の消去と、新しい名前での作成

イベントの列は、リストの名前ごとに分かれて意味を持ちます。名前を変えると、旧い名前のリストは無くなり、新しい名前のリストが、項目を引き継いで現れます。これを、イベントで表します。

| 順 | イベント | 意味 |
| :--- | :--- | :--- |
| 1 | `ListRenamed(user, 旧い名前, 新しい名前)` | 旧い名前のリストが無くなる |
| 2 | `ListCreated(user, 新しい名前)` | 新しい名前のリストが作られる |
| 3 以降 | `ItemAdded(user, 新しい名前, 項目)` | 項目が、状態ごと引き継がれる |

コマンドとイベントを足すと、第 7 章以来おなじみのことが起きました。コンパイラが、`match` の更新漏れを指摘したのです。今回は 5 か所でした。

```bash
>> Non-exhaustive match: missing case 'ListRenamed(_, _, _)'.
>> Non-exhaustive match: missing case 'RenameToDoList(_, _, _)'.
```

指摘された 5 か所は、`Events.apply`（状態への適用）、`Commands.handle`（判断）、`Projection` の 2 つの関数（射影と持ち主の取り出し）、`Codec.encode`（データベースの行への変換）です。足したイベント 1 つの影響範囲を、コンパイラが一覧にしてくれました。

判断は、これまでと同じ形です。

```flix
            case ToDoListCommand.RenameToDoList(user, name, newName) =>
                forM (
                    list <- requireList(state, user, name);
                    _    <- requireAbsent(state, user, newName)
                ) yield renamedEvents(user, name, newName, list)
```

元のリストが無ければ `ListNotFound`、新しい名前がすでにあれば `ListAlreadyExists` です。

```flix
    def requireAbsent(state: ToDoListState, user: User, name: ListName): Result[ZettaiError, Unit] =
        match Events.listFor(state, user, name) {
            case Some(_) => Err(ZettaiError.ListAlreadyExists(user, name))
            case None    => Ok(())
        }
```

同じ名前への変更は、新しい名前がすでにある、として拒否されます。

イベントは、旧い名前の消去、新しい名前での作成、項目の引き継ぎの順です。

```flix
    def renamedEvents(user: User, name: ListName, newName: ListName, list: ToDoList): List[ToDoListEvent] =
        match list {
            case ToDoList.ToDoList(_, items) =>
                ToDoListEvent.ListRenamed(user, name, newName) ::
                    ToDoListEvent.ListCreated(user, newName) ::
                    List.map(item -> ToDoListEvent.ItemAdded(user, newName, item), items)
        }
```

項目は `ItemAdded` が状態ごと運ぶので、完了した項目は完了のまま引き継がれます。名前を変えて元に戻しても、項目は二重になりません。

```flix
    @Test
    def renamingAndRenamingBackDoesNotDuplicateItems(): Unit \ Assert = {
        let first = history() ::: Result.getWithDefault(Nil, Commands.handle(RenameToDoList(uberto(), book(), shop()), state()));
        let second = first ::: Result.getWithDefault(Nil,
            Commands.handle(RenameToDoList(uberto(), shop(), book()), Replay.replayFrom(Events.empty(), first)));
        Assert.assertEq(
            expected = Events.listFor(state(), uberto(), book()),
            Events.listFor(Replay.replayFrom(Events.empty(), second), uberto(), book())
        )
    }
```

第 10 章のストアは、1 回の書き込みを 1 つのトランザクションにします。名前変更の 3 種類のイベントは、1 回の書き込みで一緒に確定するので、片方だけが残ることはありません。

## 2 つのパラメータを持った検証

### 入力の誤りは 1 つとは限らない

名前変更の入力は、利用者名、リスト名、新しい名前の 3 つです。それぞれに規則があります。

| 規則 | 誤り |
| :--- | :--- |
| 空でない | `Blank` |
| 40 文字以内 | `TooLong` |
| `/` と制御文字を含まない | `ForbiddenCharacter` |

誤りの型は、**誤りのもとになった入力の値を持ちません。**

```flix
    pub enum FieldProblem with Eq, ToString {
        case Blank(InputField)
        case TooLong(InputField, Int32)
        case ForbiddenCharacter(InputField)
    }
```

どの欄の、どの規則を破ったかだけを持ちます。値を持たない理由は後で書きます。

最初の誤りで止まる検証は、`Result` の連鎖で書けます。`forM` は前の結果を使って次を決める形なので、前が失敗すれば次は実行されません。

```flix
    def stopsAtTheFirstProblem(field: InputField, raw: String): Result[FieldProblem, String] =
        forM (
            _ <- Validate.toResult(Validate.notBlank(field, raw));
            _ <- Validate.toResult(Validate.withinLength(field, raw));
            _ <- Validate.toResult(Validate.noForbiddenCharacter(field, raw))
        ) yield raw
```

ところが、この検証は、前の結果を使っていません。空かどうかと、長さと、使えない文字は、**互いに独立**です。独立した検証が、1 つ失敗した途端に止まる理由はありません。

`/` を 41 個並べた名前で、止まる様子を確かめました。

```flix
    @Test
    def chainingWithFlatMapHidesTheSecondProblem(): Unit \ Assert = {
        let raw = String.repeat(41, "/");
        Assert.assertEq(expected = Err(TooLong(NewName, 40)), stopsAtTheFirstProblem(NewName, raw));
        Assert.assertEq(expected = 2, List.length(problems(Validate.validateName(NewName, raw))))
    }
```

連鎖は `TooLong` だけを返し、`ForbiddenCharacter` が隠れます。利用者は、長さを直して再送信してから、初めて使えない文字を知ることになります。

## バリデーションを使った検証

### 誤りを集める合成

誤りを集めるには、失敗した側の誤りを捨てずに、順に連結します。Flix の標準ライブラリには、そのための型 `Validation[e, a]` があります。成功なら値を、失敗なら**空でない**誤りの並び（`Nec`）を持ちます。

失敗の側が `Nec` であることは、最初の試みで知りました。リストの `Nel` を渡したところ、次のエラーになりました。

```bash
>> Unable to unify the types: 'Nec[String]' and 'Nel[String]'.
```

`Nec` は、空でない連鎖です。失敗しているなら、誤りは必ず 1 つ以上あります。誤りが 0 個の失敗を、型が許しません。

`forA` は、独立した計算を合成して、誤りを順に集めます。形は `forM` と同じですが、前の結果を使えない代わりに、すべてを実行します。

```flix
    pub def validateName(field: InputField, raw: String): Validation[FieldProblem, String] =
        forA (
            _ <- notBlank(field, raw);
            _ <- withinLength(field, raw);
            _ <- noForbiddenCharacter(field, raw)
        ) yield raw
```

3 つの規則は、それぞれ小さな関数です。

```flix
    pub def notBlank(field: InputField, raw: String): Validation[FieldProblem, Unit] =
        if (String.isEmpty(String.trim(raw))) fail(FieldProblem.Blank(field)) else Validation.Success(())
```

```flix
    pub def noForbiddenCharacter(field: InputField, raw: String): Validation[FieldProblem, Unit] =
        if (String.exists(c -> c == '/' or Char.isISOControl(c), raw)) fail(FieldProblem.ForbiddenCharacter(field)) else Validation.Success(())
```

制御文字を調べる関数の名前は、すぐには分かりませんでした。`Char.isControl` と `Char.toInt32` は未定義で、`Char.isISOControl` が使えました。

`/` を 41 個並べた名前は、`TooLong` と `ForbiddenCharacter` の両方を、規則の順に返します。

```flix
    @Test
    def everyBrokenRuleOfOneInputIsReportedInRuleOrder(): Unit \ Assert =
        Assert.assertEq(
            expected = List#{TooLong(NewName, 40), ForbiddenCharacter(NewName)},
            problems(Validate.validateName(NewName, String.repeat(41, "/")))
        )
```

3 つの入力にまたがる合成も、同じ形です。誤りは入力の順に集まります。

```flix
    pub def validateRename(rawUser: String, rawList: String, rawNewName: String): Validation[FieldProblem, ToDoListCommand] =
        forA (
            user    <- validateName(InputField.UserName, rawUser);
            list    <- validateName(InputField.ListNameField, rawList);
            newName <- validateName(InputField.NewName, rawNewName)
        ) yield ToDoListCommand.RenameToDoList(User.User(user), ListName.ListName(list), ListName.ListName(newName))
```

```flix
    @Test
    def everyBrokenInputOfOneRequestIsReportedInInputOrder(): Unit \ Assert =
        Assert.assertEq(
            expected = List#{Blank(UserName), ForbiddenCharacter(ListNameField), TooLong(NewName, 40)},
            problems(Validate.validateRename(" ", "a/b", tooLongName()))
        )
```

すべての入力が正しければ、名前変更のコマンドができます。誤りがあれば、コマンドは作られません。

## アプリカティブファンクタの結合

### 合成が満たす性質

`forA` は、独立した計算を合成する、アプリカティブの形をしています。`forM` の連鎖（モナドの束縛）と違い、前の結果を使いません。そのかわり、すべての誤りを見ることができます。

求める性質を、第 5 章からの流儀で、起こりうる入力の全パターンで確かめました。入力は 6 通りで、成功するもの、1 つの規則を破るもの、2 つ破るもの、3 つ破るものを含みます。

```flix
    def raws(): List[String] = List#{
        "ok",
        "",
        "a/b",
        String.repeat(41, "x"),
        String.repeat(41, "/"),
        "   "
    }
```

3 つの入力の組み合わせは、6 × 6 × 6 で 216 通りです。

| 求める性質 | 対応するテスト |
| :--- | :--- |
| すべて成功なら、ただの関数の適用と同じ | `whenEverythingSucceedsItIsPlainFunctionApplication` |
| 誤りは、各入力の誤りを入力の順に並べたものと同じ | `theProblemsOfARequestAreTheProblemsOfEachInputInOrder` |
| 誤りの個数は、失敗した部分の個数の合計 | `theNumberOfProblemsIsTheSumOfTheFailingParts` |
| 組み合わせ方を変えても、誤りの並びは同じ | `groupingTheCombinationDifferentlyDoesNotChangeTheProblems` |
| 何もしない成功と合成しても、結果は変わらない | `aSuccessOfUnitChangesNothing` |
| `map` の恒等則と合成則 | `mapPreservesIdentityAndComposes` |

組み合わせ方の性質（結合律）は、次のように確かめます。左から合成しても、右から合成しても、誤りの並びは同じです。

```flix
    @Test
    def groupingTheCombinationDifferentlyDoesNotChangeTheProblems(): Unit \ Assert =
        each3((a, b, c) -> {
            let va = Validate.validateName(UserName, a);
            let vb = Validate.validateName(ListNameField, b);
            let vc = Validate.validateName(NewName, c);
            let leftFirst = forA (ab <- forA (x <- va; y <- vb) yield (x, y); z <- vc) yield (ab, z);
            let rightFirst = forA (x <- va; bc <- forA (y <- vb; z <- vc) yield (y, z)) yield (x, bc);
            Assert.assertEq(expected = problems(leftFirst), problems(rightFirst))
        })
```

注意が 1 つあります。ここで確かめたのは、**`forA` に求める性質**です。アプリカティブ則そのもの（準同型則など）を、一般の形でテストしたわけではありません。表の対応は、則の言い換えであって、則の証明ではありません。

### 壊れた合成を見分けられるか

全パターンを走査するテストは、何も確かめていなくても通ってしまいます。第 7 章・第 9 章と同じく、壊れた実装を用意しました。最初の誤りだけを残してしまう合成です。

```flix
    def firstOnly(a: Validation[FieldProblem, String], b: Validation[FieldProblem, String]): Validation[FieldProblem, String] =
        match a {
            case Validation.Failure(e) => Validation.Failure(e)
            case Validation.Success(_) => b
        }
```

誤りの個数のテストは、この違いを見分けます。

```flix
    @Test
    def theCountTestTellsAFirstOnlyCombinationApart(): Unit \ Assert = {
        let a = Validate.validateName(UserName, "");
        let b = Validate.validateName(NewName, "");
        Assert.assertEq(expected = 2, List.length(problems(forA (x <- a; y <- b) yield x + y)));
        Assert.assertEq(expected = 1, List.length(problems(firstOnly(a, b))))
    }
```

モナドとの違いは 1 点です。モナドは、前の結果を見て次の計算を選べます。アプリカティブは、選べないかわりに、独立した計算をすべて実行できます。

## ユーザインタフェースの改善

### 誤りを、まとめて返す

検証の結果を、HTTP の応答にします。まず、誤りを失敗の型に載せます。

```flix
    pub def toZettai(v: Validation[FieldProblem, a]): Result[ZettaiError, a] =
        match v {
            case Validation.Success(a)      => Ok(a)
            case Validation.Failure(errors) => Err(ZettaiError.InvalidInput(Nec.toList(errors)))
        }
```

失敗の型にケースを足したので、第 7 章以来おなじみの更新漏れを、コンパイラが指摘しました。入力の誤りは 400 に写します。

利用者に見せる説明は、誤りの種類から作ります。

```flix
    pub def describe(problem: FieldProblem): String =
        match problem {
            case FieldProblem.Blank(field)              => "${label(field)}は空にできません"
            case FieldProblem.TooLong(field, limit)     => "${label(field)}は ${limit} 文字以内にしてください"
            case FieldProblem.ForbiddenCharacter(field) => "${label(field)}に使えない文字が含まれています"
        }
```

```flix
    pub def explain(problems: List[FieldProblem]): String =
        "入力に誤りがあります。" + String.intercalate("", List.map(p -> "\n- ${describe(p)}", problems))
```

**説明に入力の値は含めません。** 誤りの型が値を持たないので、持ち出そうにも持ち出せません。入力をそのまま応答やログに書くと、不正な入力が、そのまま出力に混ざる経路になるからです。

名前変更の入口は、`POST /todo/{利用者}/{リスト}/rename` で、本文を新しい名前として受けます。

```flix
    pub def routeRename(execute: ToDoListCommand -> Result[ZettaiError, Unit] \ ef, request: Request): Response \ ef =
        match String.splitOn(substr = "/", request#path) {
            case "" :: "todo" :: user :: list :: "rename" :: Nil =>
                match Validate.toZettai(Validate.validateRename(user, list, request#body)) {
                    case Err(error)   => failure(error)
                    case Ok(command)  =>
                        match execute(command) {
                            case Ok(_)    => { status = 200, contentType = "text/plain; charset=utf-8", body = "renamed" }
                            case Err(e)   => failure(e)
                        }
                }
            case _ => notFound()
        }
```

3 つの欄に誤りがあるとき、応答は 1 回で、すべてを伝えます。

```flix
    @Test
    def everyProblemIsReportedInOneResponse(): Unit \ Assert = {
        let response = Http.routeRename(executing(Ok(())), post("/todo/ /a/rename", String.repeat(41, "/")));
        Assert.assertEq(expected = 400, response#status);
        // 利用者名・リスト名・新しい名前の誤りが、1 回の応答にすべて入る
        Assert.assertTrue(contains(response#body, "利用者名"));
        Assert.assertTrue(contains(response#body, "新しい名前"));
        Assert.assertEq(expected = 3, List.length(String.splitOn(substr = "\n- ", response#body)) - 1)
    }
```

入力の値が応答に混ざらないことも、確かめます。

```flix
    @Test
    def theResponseNeverEchoesTheInput(): Unit \ Assert = {
        let response = Http.routeRename(executing(Ok(())), post("/todo/uberto/book/rename", "secret/name"));
        Assert.assertEq(expected = 400, response#status);
        Assert.assertFalse(contains(response#body, "secret"))
    }
```

ドメインが断った場合は、それぞれの状態を保ちます。リストが無ければ 404、新しい名前がすでにあれば 400 です。

### 実サーバーを通す

最後に、実サーバーを起動して、通しで確かめます。読み取り用の入口を、イベントストアの上に作りました。

```flix
    pub def fromStore(store: EventStore[ef]): ToDoListHub[ef] =
        todoListHub((user, name) -> match store#load() {
            case Err(e)   => Err(e)
            case Ok(read) =>
                match Events.listFor(Replay.replayFrom(Events.empty(), read#events), user, name) {
                    case Some(list) => Ok(list)
                    case None       => Err(ZettaiError.ListNotFound(user, name))
                }
        })
```

テストは、リストを作って項目を足し、正しい要求で名前を変え、誤りのある要求を送ります。

```flix
            let (renamed, _) = HttpTestClient.post(port, "/todo/uberto/book/rename", "shop");
            let (oldStatus, _) = HttpTestClient.get(port, "/todo/uberto/book");
            let (newStatus, newBody) = HttpTestClient.get(port, "/todo/uberto/shop");
            // 誤りのある要求なら、誤りがすべて返り、何も変わらない
            let (badStatus, badBody) = HttpTestClient.post(port, "/todo/uberto/shop/rename", String.repeat(41, "/"));
            let (stillThere, _) = HttpTestClient.get(port, "/todo/uberto/shop");
```

結果は、期待どおりです。

- 正しい要求は 200 で、旧い名前は 404、新しい名前は 200 になり、項目が引き継がれている
- 誤りのある要求は 400 で、「40 文字以内」と「使えない文字」の両方が返り、リストは変わらない

`Request` に本文（`body`）を足したとき、コンパイラが直す場所を 3 つ指摘しました（`JdkServer`、`TestEffectDi`、`TestHttpApp`）。型を変えた波及が、見えるかたちで出ます。

## まとめ

- リスト名の変更を、旧い名前の消去、新しい名前での作成、項目の引き継ぎのイベントで表しました。イベントとコマンドを足すと、コンパイラが 5 か所の更新漏れを指摘しました。
- 独立した検証を `Result` の連鎖で書くと、最初の誤りで止まり、2 つ目の誤りが隠れることを確かめました。
- 標準の `Validation` と `forA` で、誤りを規則の順、入力の順に、すべて集めました。失敗側は空でない連鎖 `Nec` で、誤りが 0 個の失敗を型が許しません。
- 誤りの個数、並び、組み合わせ方の性質を、216 通りの全パターンで確かめました。最初の誤りだけを残す壊れた合成を、個数のテストが見分けることも示しました。
- 誤りの型は入力の値を持たず、HTTP の応答に入力が混ざりません。誤りは 1 回の応答にすべて入ります。
- 実サーバーを通して、正しい要求は名前を変え、誤りのある要求は何も変えないことを確かめました。
- `flix test` は 129 本が通ります。

つまずきとして、制御文字を調べる関数の名前が分からず、`Char.isControl` と `Char.toInt32` は未定義でした。複数の候補を試して、`Char.isISOControl` に行き着きました。

次章では、アプリケーションを監視します。ログに何を載せるかを、型で決められるかを考えます。誤りの型が値を持たなかった設計が、ここでも効いてきます。

## この章で書いたコード

- バリデーション: `apps/flix/zettai/src/Validate.flix`
- 誤りの型: `apps/flix/zettai/src/Errors.flix`
- 名前変更の判断: `apps/flix/zettai/src/Commands.flix`
- HTTP の入口: `apps/flix/zettai/src/Http.flix`
- テスト: `apps/flix/zettai/test/TestValidation.flix`、`TestApplicativeLaws.flix`、`TestRename.flix`、`TestRenameRoute.flix`、`TestRenameOverHttp.flix`

## 参照

- Uberto Barbini『From Objects to Functions』第 11 章
- [Flix 公式ドキュメント](https://doc.flix.dev/)
