---
type: Article
title: "第 12 章 監視と関数型 JSON"
description: "Zettai 連載 Scala 版の第 12 章。ログの 1 行を型付きの LogRecord で表し、操作を外から包むデコレータで記録する。JSON を自作し、境界値と特殊文字の往復を確かめる。dimap と imap で、型の位置が使える道具を決めることを確かめ、ログのテストが見つけた二重読み出しを直す。"
tags: [article, zettai, scala, cats-effect, logging, json, profunctor, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-02T16:30:00Z }
---

# 第 12 章 監視と関数型 JSON

運用では、何が起きているかを知る手段が要ります。この章では、アプリケーションの操作を **1 行のログ** として記録します。載せるものを型で決め、JSON は自作します。ライブラリを借りるか自作するかの判断は、先に [ADR-052](../../../adr/ADR-052-scala-own-json-and-typed-log-record.md) に記録しました。

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

ログを足す方法は、大きく 2 つあります。操作の中に `println` のような出力を書き足すか、操作を包むかです。

### ドメインにログを足せるか

Flix では、標準の `Logger` 効果が型に現れ、ドメインの関数にログを足すとコンパイルが拒否しました。**Scala には、この守りがありません。** 第 1 章で、`println` を入れても `sbt compile` が通ることを確かめました。

ドメインの関数にログを足さないことは、型ではなく、規律で守ります。そのため、載せるものを決める型を用意し、操作を外から包みます。

### 包む対象

`Hub`、`CommandHub`、`EventStore` は、どれも関数を持つ値です。関数を 1 つずつ包めば、元の関数には 1 行も足さずに、記録を加えられます。

| 包むもの | 載せる種類 |
| :--- | :--- |
| 読み取りの入口 `Hub` | `hub` / `getList` |
| コマンドの入口 `CommandHub` | `command` / コマンドの種類 |
| イベントの保管場所 `EventStore` | `store` / `load`・`append` |

## 構造化されたロギング

### 載せるものを、型で決める

ログの 1 行は、自由な文字列ではなく、決まった欄を持つ型です。

```scala
final case class LogRecord(
    kind: String,
    name: String,
    outcome: String,
    user: Option[String],
    list: Option[String],
    durationMs: Int
)
```

項目の説明、イベントの中身、失敗の理由は、入れる場所が型にありません。**書かないという規律ではなく、書けないことで守られます。**

失敗の種類は、理由を持たない名前にします。`ZettaiError` に新しいケースを足したら、この `match` がコンパイルエラーで教えてくれます。

```scala
  def category(error: ZettaiError): String =
    error match
      case ZettaiError.ListNotFound(_, _)         => "ListNotFound"
      case ZettaiError.ListAlreadyExists(_, _)    => "ListAlreadyExists"
      case ZettaiError.ItemNotFound(_, _, _)      => "ItemNotFound"
      case ZettaiError.InvalidTransition(_, _, _) => "InvalidTransition"
      case ZettaiError.VersionConflict(_, _)      => "VersionConflict"
      case ZettaiError.InvalidInput(_)            => "InvalidInput"
```

### 操作を包む

操作を実行し、結果にかかわらず 1 行を書きます。結果そのものは、そのまま返します。

```scala
  def observe[A](
      sink: LogSink,
      clock: IO[Long],
      kind: String,
      name: String,
      user: Option[String],
      list: Option[String],
      operation: IO[Either[ZettaiError, A]]
  ): IO[Either[ZettaiError, A]] =
    for
      startedAt  <- clock
      attempted  <- operation.attempt
      finishedAt <- clock
      outcome     = attempted.fold(_ => "UnexpectedFailure", outcomeOf)
      duration    = math.min(finishedAt - startedAt, Int.MaxValue.toLong).toInt
      line        = Json.render(Logs.toJson(LogRecord(kind, name, outcome, user, list, duration)))
      _          <- sink.write(line).handleError(_ => ())
      result     <- IO.fromEither(attempted)
    yield result
```

3 つのことを決めています。

- 業務の失敗（`Left`）は、種類を記録して、そのまま返す
- 想定外の失敗（`IO` の失敗）は、`UnexpectedFailure` とだけ記録して、投げ直す。理由は載せない
- ログの出力先が壊れても、処理は止めない（`handleError(_ => ())`）

コマンドの入口は、コマンドの種類・利用者・リストだけを載せます。項目の説明は載せません。

```scala
  def observeCommandHub(sink: LogSink, clock: IO[Long], hub: CommandHub): CommandHub =
```

### ログの出力先を、テストが握る

出力先は `LogSink` という、1 行を書く関数を持つ値です。

```scala
final case class LogSink(write: String => IO[Unit])
```

テストは、書かれた行を集める出力先に差し替えます。時計も差し替えて、1 回呼ぶたびに 100 ミリ秒進めます。

```scala
  private def collecting: IO[(LogSink, IO[List[String]])] =
    Ref.of[IO, List[String]](Nil).map(lines => (LogSink(line => lines.update(_ :+ line)), lines.get))
```

```scala
      assertEquals(
        written.map(parse),
        List(
          LogRecord("command", "CreateToDoList", "ok", Some("uberto"), Some("book"), 100),
          LogRecord("command", "AddToDoItem", "ListNotFound", Some("uberto"), Some("nothing"), 100)
        )
      )
      assert(written.forall(!_.contains("secret item")))
```

拒否された操作でも 1 行が書かれ、項目の説明（`secret item`）はどの行にも現れません。

想定外の失敗は、理由に秘密の文字列を入れて確かめます。種類だけが記録され、理由は行に出ず、元の失敗はそのまま投げ直されます。

```scala
    val secret = "jdbc:postgresql://db.internal/zettai password=hunter2"
```

```scala
      assertEquals(outcome.left.map(_.getMessage), Left(secret))
      assertEquals(written.map(parse), List(LogRecord("store", "load", "UnexpectedFailure", None, None, 100)))
      assert(written.forall(!_.contains("hunter2")))
```

出力先が壊れても、処理が止まらないことも確かめました。`handleError(_ => ())` を外すと、このテストだけが `disk full` で落ちます。

```scala
  test("ログの出力先が壊れても、処理は止まらない") {
```

## JSON を関数型にする

### 文字列を手で組み立てない

ログの 1 行は JSON です。文字列を手で連結すると、引用符や改行の取り扱いを間違えます。木を作って、書き出します。

```scala
enum JsonTree:
  case JNull
  case JInt(value: Int)
  case JString(value: String)
  case JArray(items: List[JsonTree])
  case JObject(fields: List[(String, JsonTree)])
```

Scala の標準ライブラリに JSON はありません。ADR-052 のとおり、自作します。

```scala
  def render(tree: JsonTree): String =
    tree match
      case JNull          => "null"
      case JInt(n)        => n.toString
      case JString(s)     => escape(s)
      case JArray(items)  => items.map(render).mkString("[", ",", "]")
      case JObject(kvs)   => kvs.map((k, v) => s"${escape(k)}:${render(v)}").mkString("{", ",", "}")
```

### 木と文字列の双方向変換

読み込みは、読めないものを例外ではなく失敗の値にします。

```scala
  def parse(text: String): Either[String, JsonTree] =
```

書き出して読み戻すと元の木に戻ることを、小さな範囲の全パターンで確かめます。特殊文字（引用符、バックスラッシュ、改行、制御文字、日本語、絵文字）と、`Int` の最小値・最大値を含めます。

```scala
  test("書き出して読み戻すと、元の木に戻る（小さな範囲の全パターン）") {
```

### 境界値の整数

整数は、符号つきで読んで範囲を確かめます。`Int` の最小値は、桁だけを先に `Int` に読むと読めません。

```scala
      val value = BigInt(text.substring(from, i))
      if value.isValidInt then Right((JInt(value.toInt), i)) else Left(s"integer out of range at $from")
```

Flix 版は、自作の JSON が `Int32` の最小値を読めないバグを、往復のテストで見つけました。Scala 版は、そのつまずきを知ってから書いたので、最初から `BigInt` で範囲を確かめました。テストが境界値を見分けられるかを確かめるため、桁を先に `toInt` で読む実装に書き換えました。3 本のテストが `NumberFormatException` で落ちました。元に戻すと、すべて通ります。

```scala
  test("Int の最小値と最大値を、書き出して読み戻せる") {
    for n <- List(Int.MinValue, Int.MaxValue, 0, -1, 1) do assertEquals(Json.parse(Json.render(JInt(n))), Right(JInt(n)))
  }
```

ログの 1 行は、木を経由して書き出し、読み戻せます。

```scala
  test("書き出して読み戻すと、元の記録に戻る") {
    for r <- List(record, record.copy(user = None, list = None), record.copy(durationMs = Int.MaxValue)) do
      assertEquals(Logs.fromJson(Logs.toJson(r)), Right(r))
  }
```

## プロファンクタとの出会い

### 入力の側と出力の側

関数 `A => B` は、入力 `A` が反変、出力 `B` が共変の位置にあります。**入力を読み替え、出力を書き換える**操作が `dimap` です。

```scala
  def dimap[A, A2, B, B2](pre: A2 => A, post: B => B2, f: A => B): A2 => B2 =
    x => post(f(pre(x)))
```

入力は逆向きに、出力は順向きに写ります。

```scala
  test("入力は逆向きに、出力は順向きに写る") {
    val shout = Dimap.dimap((s: String) => s.length, (n: Int) => "*" * n, (n: Int) => n + 1)
    assertEquals(shout("abc"), "****")
  }
```

恒等関数を渡しても何も変わらず、2 回の `dimap` は合成できます。自作の `dimap` は、Cats の `Profunctor` の `dimap` と一致しました。

```scala
  test("自作の dimap は、Cats の Profunctor の dimap と一致する") {
    for f <- base; p <- pre; q <- post; x <- domain do
      assertEquals(Dimap.dimap(p, q, f)(x), Profunctor[Function1].dimap(f)(p)(q)(x))
  }
```

### 双方向の束には、一般の dimap が無い

`dimap` は、関数には作れます。では、木との双方向変換には作れるでしょうか。

```scala
final case class Converter[A](encode: A => JsonTree, decode: JsonTree => Either[String, A])
```

型 `A` は、`encode` の入力にも、`decode` の出力にも現れます。つまり、**反変の位置と共変の位置の両方に現れます。** 入力だけを読み替える写像、出力だけを書き換える写像では、どちらかの側が合いません。型を変えるには、行きと戻りの**両方向**の写像が要ります。Cats では、これを `Invariant` の `imap` と呼びます。

```scala
  given Invariant[Converter] with
    def imap[A, B](fa: Converter[A])(f: A => B)(g: B => A): Converter[B] =
      Converter(b => fa.encode(g(b)), json => fa.decode(json).map(f))
```

行きと戻りの写像を渡せば、型を変えられます。

```scala
  test("型を変えるには、行きと戻りの両方向の写像が要る（imap）") {
    val lengthOf = Invariant[Converter].imap(Converter.string)(_.length)("x".repeat(_))
    assertEquals(lengthOf.encode(3), JString("xxx"))
    assertEquals(lengthOf.decode(JString("xxx")), Right(3))
  }
```

関数には `dimap`、双方向の束には `imap`。**型の位置が、使える道具を決めます。**

## データベース呼び出しのロギング

### 保管場所を包む

`EventStore` を包むと、データベースへの呼び出しが記録されます。載せるのは、`load` と `append` の名前、結果の種類、所要時間だけです。

```scala
  def observeStore(sink: LogSink, clock: IO[Long], store: EventStore): EventStore =
```

### 組み立てる

3 つの入口を、すべて同じ出力先と時計で包んで組み立てます。保管場所は内側に置きます。

```scala
      store     = Logging.observeStore(pair._1, clock, inner)
      commands  = Logging.observeCommandHub(pair._1, clock, CommandHub.commandHub(store))
      hub       = Logging.observeHub(pair._1, clock, Hub.fromStore(store))
```

### 内側の記録が先に並ぶ

実際に動かして、どの順に記録が並ぶかを確かめました。保管場所の記録が先に、入口の記録があとに並びます。

```scala
      assertEquals(
        records.map(r => (r.kind, r.name, r.outcome)),
        List(
          ("store", "load", "ok"),
          ("store", "append", "ok"),
          ("command", "CreateToDoList", "ok"),
```

入口の操作は、内側の保管場所の呼び出しを含むので、保管場所の記録が先に書かれます。読み取りは、`store load` のあとに `hub getList` が並びます。存在しないリストの読み取りは、`ListNotFound` で記録されます。

### 置き場所を間違えた（ログのテストが見つけたこと）

このテストを最初に書いたとき、期待した並びと、実際の並びが違いました。コマンドを 1 回実行すると、`store load` が **2 回** 記録されていました。

```bash
store load / store load / store append / command CreateToDoList
```

原因は第 9 章の `CommandHub` にありました。`execute` が、版を知るために 1 回、状態を作るために 1 回、続けて `load` を呼んでいたのです。通常の動作は正しいのですが、**2 回の読み出しの間に別の書き手が書くと、判断に使った状態と添える版が食い違いかねません。** 状態のほうが新しいときは、版の確認で断られるので安全ですが、無駄な読み出しです。

判断に使う状態と添える版を、同じ 1 回の読み出しから取るように直しました。

```scala
    def decideAndAppend(expected: Int, read: Versioned, command: ToDoListCommand): IO[Either[ZettaiError, Unit]] =
```

```scala
      command => store.load.flatMap(read => decideAndAppend(read.version, read, command)),
```

直したあと、`store load` は 1 回だけ記録されます。**ログは、運用のためだけでなく、実装の無駄を見つける観測にもなりました。**

## まとめ

- ログの 1 行を、決まった 6 つの欄を持つ型 `LogRecord` で表した。載せてはならないものは、書けない
- Scala の型はドメインの関数へのログの混入を止めないので、操作を外から包むデコレータと、型付きの記録で守る
- 想定外の失敗は、種類だけを記録して投げ直す。出力先が壊れても、処理は止めない
- 出力先と時計をテストが握り、行の内容と、秘密が出ないことを確かめた
- JSON は自作した。往復を小さな範囲の全パターンと境界値で確かめ、桁を先に `toInt` で読む実装では 3 本が落ちる
- `dimap` は関数に、`imap` は双方向の束に使える。型の位置が、使える道具を決める
- 保管場所の記録が、二重の読み出しを見つけた

`sbt test` は 130 本が通ります（第 11 章の 109 本に、21 本を足しました）。

次章では、連載全体を見渡して、5 つの対象を並べます。

## この章で書いたコード

- `apps/scala/zettai/src/main/scala/zettai/Json.scala`
- `apps/scala/zettai/src/main/scala/zettai/Converter.scala`
- `apps/scala/zettai/src/main/scala/zettai/Dimap.scala`
- `apps/scala/zettai/src/main/scala/zettai/Logs.scala`
- `apps/scala/zettai/src/main/scala/zettai/Logging.scala`
- `apps/scala/zettai/src/main/scala/zettai/CommandHub.scala`
- `apps/scala/zettai/src/test/scala/zettai/JsonSuite.scala`
- `apps/scala/zettai/src/test/scala/zettai/LogRecordSuite.scala`
- `apps/scala/zettai/src/test/scala/zettai/ConverterSuite.scala`
- `apps/scala/zettai/src/test/scala/zettai/ProfunctorSuite.scala`
- `apps/scala/zettai/src/test/scala/zettai/LoggingSuite.scala`
- `apps/scala/zettai/src/test/scala/zettai/LoggingOverHttpSuite.scala`

## 参照

- [ADR-052 JSON と型付きのログ記録を自作する](../../../adr/ADR-052-scala-own-json-and-typed-log-record.md)
- [Cats の Invariant](https://typelevel.org/cats/typeclasses/invariant.html)
- [Cats の Profunctor](https://typelevel.org/cats/typeclasses/arrow.html)
