---
type: Article
title: "第 2 章 関数を使って HTTP を扱う"
description: "Zettai 連載 Flix 版の第 2 章。純粋な route 関数と JDK の HttpServer を包むアダプタで HTTP の縦串を通し、Java 相互運用の境界で効果の保証がどこまで届くかを記録する。"
tags: [article, zettai, flix, http, java-interop, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-02T15:00:00Z }
---

# 第 2 章 関数を使って HTTP を扱う

第 1 章では、純粋関数と効果の関係を小さな例で確かめました。この章ではその考え方を HTTP に持ち込みます。リクエストが入り、ドメインの値を取得し、HTML が返る最小の縦串を通します。

Flix 版は 1 つのプロジェクトを章ごとに育てていくため、後の章で書き換わったコードがあります。そのコードブロックには、この章を書いた時点のコミットを示してあります。

Flix には HTTP サーバーの標準ライブラリがありません。この版では JDK の `com.sun.net.httpserver` を **Java 相互運用で呼びます**。公式ドキュメントには「Java とのやりとりは常に `IO` 効果を持つ」とあり、効果の保証は Java の境界で切れます。（公開後に分かったこと: 実際は、既定が `IO` で、コンパイラが表で純粋と分類した呼び出しだけが例外です。[第 13 章](chapter13.md)）この章はその切れ目を、実際にコンパイラの出力で確かめる章です。

## プロジェクトのキックオフ

最初の到達点は `GET /todo/uberto/book` です。`uberto` の `book` リストがあれば項目を HTML で返し、なければ `404 Not Found` を返します。作成、更新、データベースはまだ扱いません。

この薄い縦串はウォーキングスケルトンです。HTTP、ドメイン、表示の接続を早く確かめ、後の章で安全に太くできる骨格を作ります。

まず外側から受入テストを書きました。サーバーを起動して本物の HTTP で叩きます。

```flix
    @Test
    def servesTheListOverRealHttp(): Unit \ {Assert, IO} = {
        let server = Web.JdkServer.start(0, app);
        let port = Web.JdkServer.port(server);
        let (status, body) = HttpTestClient.get(port, "/todo/uberto/book");
        Web.JdkServer.stop(server);
        Assert.assertEq(expected = 200, status);
        Assert.assertTrue(String.contains(substr = "<li>write chapter</li>", body))
    }
```

`JdkServer` はまだ存在しないため、コンパイルが失敗します。これを Red とし、テストを通すのに必要なものだけを足します。

```bash
>> Undefined name 'JdkServer.port'.
>> Undefined name 'JdkServer.start'.
>> Undefined name 'JdkServer.stop'.

Compilation failed with 6 error(s).
```

ポート `0` は OS に空きポートを選ばせる指定です。固定ポートを使わないので、テストが他のプロセスと衝突しません。

## 関数型で HTML ページを提供する

HTTP を関数として見ると、リクエストを受けてレスポンスを返す `Request -> Response` です。この関数をサーバーから切り離して書けば、サーバーを起動せずに検証できます。

`Request` と `Response` は、Flix のレコードに型の別名を付けて表します。

<!-- code-check: ignore この章を書いた時点のコード（コミット 398e0e6）。第 11 章で Request に body を足した -->
```flix
    pub type alias Request = { method = String, path = String }

    pub type alias Response = { status = Int32, contentType = String, body = String }
```

ルーティングは次の純粋関数です。取得関数 `fetch` を引数に取り、パスをドメインの値へ変換して呼び、結果をレスポンスへ変換します。

<!-- code-check: ignore この章を書いた時点のコード（コミット 398e0e6）。第 4 章で Hub（関数値のレコード）に置き換えた -->
```flix
    pub def route(fetch: (User, ListName) -> Option[ToDoList] \ ef, request: Request): Response \ ef =
        if (request#method != "GET")
            methodNotAllowed()
        else
            match String.splitOn(substr = "/", request#path) {
                case "" :: "todo" :: user :: list :: Nil =>
                    match fetch(User.User(user), ListName.ListName(list)) {
                        case Some(todoList) => html(200, Web.Html.render(todoList))
                        case None           => notFound()
                    }
                case _ => notFound()
            }
```

`\ ef` は第 1 章の `loadGreeting` と同じ**効果多相**です。`route` は取得関数の効果をそのまま引き継ぎます。インメモリの取得関数を渡せば `route` は純粋に、第 9 章でデータベースを読む関数を渡せば `IO` を持つものになります。HTTP の判断を書き換えずに、取得元だけを差し替えられます。

テストは、サーバーを起動せず関数を直接呼びます。

<!-- code-check: ignore この章を書いた時点のコード（コミット 398e0e6）。第 4 章で Hub（関数値のレコード）に置き換えた -->
```flix
    @Test
    def returnsTheListAsHtml(): Unit \ Assert = {
        let response = Web.Http.route(fetcher, get("/todo/uberto/book"));
        Assert.assertEq(expected = 200, response#status);
        Assert.assertEq(expected = "text/html; charset=utf-8", response#contentType);
        Assert.assertTrue(String.contains(substr = "<li>write chapter</li>", response#body))
    }
```

テストの型が `Unit \ Assert` で、`IO` を含まないことに注目してください。**`route` が純粋であることを、テストのシグネチャが証明しています。**

## Zettai の開発を始める

ドメインは HTTP を知らない値だけで表します。

<!-- code-check: ignore この章を書いた時点のコード（コミット 398e0e6）。第 4 章で Hub（関数値のレコード）に置き換えた -->
```flix
    pub enum User(String) with Eq, Order, ToString

    pub enum ListName(String) with Eq, Order, ToString

    pub enum ToDoItem(String) with Eq, ToString

    pub enum ToDoList(ListName, List[ToDoItem]) with Eq, ToString
```

値を 1 つだけ持つ `enum` で、`String` を包んでいます。`User` と `ListName` の取り違えをコンパイラが見つけます。`with Eq, Order, ToString` は、等価性、順序、文字列化を自動導出する指定で、`User` を `Map` のキーにするために `Order` が要ります。

HTML への変換では、ドメイン値を markup として解釈させないようエスケープします。

```flix
    pub def escape(text: String): String =
        text
            |> String.replace(src = "&", dst = "&amp;")
            |> String.replace(src = "<", dst = "&lt;")
            |> String.replace(src = ">", dst = "&gt;")
            |> String.replace(src = "\"", dst = "&quot;")
```

`&` を最初に置換するのが肝心です。後に回すと、直前に作った `&lt;` の `&` までもう一度置換してしまいます。

## 矢印で設計する

依存の向きは、関数の型へ直接現れます。

```text
Request -> route -> (User, ListName) -> Option[ToDoList] -> HTML -> Response
```

`route` は保存方法を知りません。知っているのは取得関数の入力と出力だけです。純粋な検索は `Domain.findToDoList` にあります。

```flix
    pub def findToDoList(lists: Map[User, List[ToDoList]], user: User, name: ListName): Option[ToDoList] =
        Map.get(user, lists) |> Option.flatMap(userLists ->
            List.find(l -> match l { case ToDoList.ToDoList(n, _) => n == name }, userLists))
```

他の対象では、ドメインが HTTP ライブラリに依存していないことを、import を走査するテストで守りました。Flix では事情が違います。**Java のクラスを使うコードは必ず `IO` 効果を持つ**ので、`Domain` の関数が効果の注釈なしで書かれている限り、Java に触れることはコンパイルが許しません。注釈なしの関数から `System.currentTimeMillis()` を呼ぶと、第 1 章の `println` と同じ `Unexpected effect 'IO' in {} function.` で拒否されることを確かめました。境界の検査を、型検査が肩代わりします。

ただし、これは「注釈なしの関数」に限った保証です。ある関数に `\ IO` と書いてしまえば、Java を呼べます。そのときは `IO` が注釈に現れるので、レビューで見つけられます。

### 境界で効果の保証が切れる場所

`JdkServer` は JDK の `HttpServer` を包むアダプタです。Java の `HttpHandler` を、匿名クラスで実装します。

<!-- code-check: ignore この章を書いた時点のコード（コミット 398e0e6）。第 11 章で Request に body を足した -->
```flix
    pub def start(port: Int32, app: Request -> Response \ ef): HttpServer \ IO = {
        let server = HttpServer.create(new InetSocketAddress(port), 0);
        let adapter = new HttpHandler {
            def handle(_this: HttpHandler, exchange: HttpExchange): Unit \ IO + ef = {
                let request = {
                    method = exchange.getRequestMethod(),
                    path = exchange.getRequestURI().getPath()
                };
                let response = app(request);
                let bytes = response#body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", response#contentType);
                exchange.sendResponseHeaders(response#status, Int32.toInt64(Array.length(bytes)));
                let out = exchange.getResponseBody();
                out.write(bytes);
                out.close()
            }
        };
        server.createContext("/", adapter);
        server.start();
        server
    }
```

ここに 3 つの発見がありました。

**1 つ目。** 最初は `app` の型を `Request -> Response \ IO` と書きました。純粋な `app` を渡すと、コンパイラが拒否しました。

```bash
-- Type Error [E6218] ---------------------------------- test/TestJdkServer.flix

>> Mismatched effect(s): expected 'IO', but got 'Pure'.

11 |     pub def start(port: Int32, app: Request -> Response \ IO): HttpServer \ IO = {
                                         ^^^^^^^^^^^^^^^^^^^^^^^^
                                         function expected argument with effect(s) 'IO'

21 |         let server = JdkServer.start(0, app);
                                             ^^^
                                             the culprit
```

純粋な関数は `IO` を期待する引数には渡せません。**効果は「多いほうに自動で寄る」のではなく、型として厳密に一致を求められます。**

**2 つ目。** では `app` 側を `\ IO` と宣言すればよいかと思い、本体が純粋なまま `\ IO` を付けると、これも拒否されました。

```bash
-- Type Error [E6217] ---------------------------------- test/TestJdkServer.flix

16 |     def app(request: Request): Response \ IO =
                                               ^^
                                               Unused effect: 'IO'

Explanation: To fix this, either remove 'IO' from the signature
or use the effect in the function body
```

使っていない効果を宣言することも許されません。効果の注釈が嘘をつけないことの裏返しです。

**3 つ目。** 解決は、`start` を効果多相にして、引数の効果 `ef` をそのまま引き継ぐことでした。`handle` の `\ IO + ef` がそれです。ところが、外側の `start` のシグネチャにも `ef` を書くと、これも拒否されます。

```bash
11 |     pub def start(port: Int32, app: Request -> Response \ ef): HttpServer \ IO + ef = {
                                                                                      ^^
                                                                                      Unused effect: 'ef'
```

`app` が実際に呼ばれるのは、サーバーを起動した**後**、Java が `handle` を呼ぶときです。`start` を呼んだ時点では `app` は実行されないので、`ef` は `start` の効果ではありません。結果として、**`start` のシグネチャからは、`app` がどんな効果を持つかが見えません。** 効果の保証が、Java のコールバックを経由するところで切れています。

この切れ目は、この版の設計で受け入れることにしました。`app` の効果は `route` の型で追え、`JdkServer` は殻の最も外側の 1 箇所にだけあるからです。

## ToDo リストをマップで提供する

永続化より前に HTTP の設計を確かめたいので、データ供給元は `Map[User, List[ToDoList]]` です。テストは固定データを閉じ込めた取得関数を渡します。

<!-- code-check: ignore この章を書いた時点のコード（コミット 398e0e6）。第 4 章で Hub（関数値のレコード）に置き換えた -->
```flix
    def lists(): Map[User, List[ToDoList]] =
        Map#{
            User.User("uberto") => List#{
                ToDoList.ToDoList(
                    ListName.ListName("book"),
                    List#{ToDoItem.ToDoItem("write chapter"), ToDoItem.ToDoItem("publish book")}
                )
            }
        }
```

可変の共有状態も外部サービスもありません。404 のテストも同じ関数を直接呼ぶだけです。

<!-- code-check: ignore この章を書いた時点のコード（コミット 398e0e6）。第 4 章で Hub（関数値のレコード）に置き換えた -->
```flix
    @Test
    def returnsNotFoundForAMissingList(): Unit \ Assert =
        Assert.assertEq(expected = 404, Web.Http.route(fetcher, get("/todo/uberto/missing"))#status)
```

実サーバーを通す 404 のテストも 1 本あります。ステータスコードが 400 以上のときは、`getInputStream()` ではなく `getErrorStream()` から本体を読む必要があります。これは `HttpTestClient` の中に閉じ込めました。

```flix
        let stream = if (code < 400) conn.getInputStream() else conn.getErrorStream();
```

## まとめ

- HTTP から HTML までのウォーキングスケルトンを通しました。`flix test` は 14 本が通ります。
- `route` を `Request -> Response` の純粋関数として書き、サーバーを起動せずにテストしました。
- 取得関数の効果を `\ ef` で引き継ぎ、インメモリでもデータベースでも `route` を書き換えずに済む形にしました。
- Java との相互運用は `JdkServer` の 1 箇所に閉じ込めました。
- 効果の注釈は、不足しても余っても拒否されます。そして `app` の効果は、Java のコールバックを経由する `start` の型からは見えません。**保証が切れる場所は `JdkServer` の中です。**

つまずきとして、`handler` は Flix の予約語で、変数名に使えませんでした。コンパイラは名前の位置で「`<pattern>` が来るはずです」と報告するだけなので、最初は原因が分かりにくいです。

次章では、この HTTP の詳細を受入仕様から追い出します。同じ業務シナリオを、ドメインと HTTP の 2 経路で実行します。

## この章で書いたコード

- ドメイン: `apps/flix/zettai/src/Domain.flix`
- HTML: `apps/flix/zettai/src/Web.Html.flix`
- ルーティング: `apps/flix/zettai/src/Web.Http.flix`
- JDK のサーバーを包むアダプタ: `apps/flix/zettai/src/Web.JdkServer.flix`
- テスト: `apps/flix/zettai/test/TestDomain.flix`、`TestHtml.flix`、`TestHttpApp.flix`、`TestJdkServer.flix`、`HttpTestClient.flix`

## 参照

- Uberto Barbini『From Objects to Functions』第 2 章
- [Interoperability with Java](https://doc.flix.dev/interoperability.html)
- [HttpServer (Java SE API)](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html)
