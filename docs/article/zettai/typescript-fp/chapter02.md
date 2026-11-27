---
type: Article
title: "第 2 章 関数を使って HTTP を扱う"
description: "Zettai 連載 TypeScript FP 版の第 2 章。HTTP を Request から Effect<Response> への関数として書き、node:http への接続を自作して最小の縦串を通す。素朴な接続の実装が持つ誤りをテストで見つけ、構造的型付けとオブジェクトのキーという、型検査を通ってしまう落とし穴を確かめる。"
tags: [article, zettai, typescript, effect, http, chapter]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-27T21:00:23Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-27T10:35:29Z }
---

# 第 2 章 関数を使って HTTP を扱う

この章では、`GET /todo/uberto/book` が HTML を返す、最小の縦串を通します。

Effect には HTTP の機能（`effect/http`）と、Node 用のサーバー（`@effect/platform-node`）があります。どちらも unstable です。3 通りの構成で実際にサーバーを起動して比べ、**この版では stable な部品だけを使い、Node のサーバーへの接続を自分で書く**ことにしました（[ADR-056](../../../adr/ADR-056-typescript-http-with-stable-parts.md)）。`effect/http` を使うより行数は増えますが、「関数で HTTP を扱う」仕組みが、すべて手元のコードになります。**この実装は、本番で使える HTTP サーバーではありません。**

## プロジェクトのキックオフ

最初の到達点は、次の 1 つです。`uberto` の `book` リストがあれば項目を HTML で返し、なければ `404` を返します。作成、更新、データベースは、まだ扱いません。

この薄い縦串を**ウォーキングスケルトン**と呼びます。HTTP、ドメイン、表示の接続を早く確かめ、後の章で安全に太くできる骨組みを作ります。

step は、`zettai-step2-http-domain` を新しく作ります。第 1 章の step は残します。

まず外側のテストを書きます。サーバーを実際に起動して、HTTP で要求を送ります。

```typescript
it("serves the items of a ToDo list over real HTTP", async () => {
  const store = new Map([
    ["uberto", [{ listName: { name: "book" }, items: [{ description: "write chapter" }] }]]
  ])
  const program = Effect.gen(function* () {
    const server = yield* serve(makeApp(findToDoList(store)), 0)
    return yield* Effect.promise(() => send(server.port, "/todo/uberto/book"))
  })

  const response = await Effect.runPromise(Effect.scoped(program))

  expect(response.status).toBe(200)
  expect(response.contentType).toBe("text/html; charset=utf-8")
  expect(response.body).toContain("<li>write chapter</li>")
})
```

ポートに `0` を指定すると、OS が空いているポートを選びます。固定のポートを使わないので、テストがほかのプロセスと衝突しません。`send` は、`node:http` で要求を送るテスト用の関数です。`fetch` は URL を書き換えることがあるので、パスを書いたとおりに送れる方を使います。

この時点では実装が何も無いので、テストは読み込みで失敗します（抜粋。パスは省略しています）。

```bash
Error: Cannot find module '../src/domain/find-todo-list.ts'
      Tests  no tests
```

ここから内側へ進み、部品を 1 つずつ作ります。最後に、この外側のテストを通します。

## 関数型でHTMLページを提供する

HTTP のサーバーは、**要求を受けて応答を返す関数**と見ることができます。この版では、Web 標準の `Request` と `Response` を使い、その関数の型を次のように決めます。

```typescript
export type App = (request: Request) => Effect.Effect<Response>
```

`Response` をそのまま返さず `Effect<Response>` にします。後の章で、データベースへの問い合わせのような、実行して初めて起きる処理が、この関数の中に入るためです。`Effect.Effect<Response>` のエラーの型は `never` です。**想定する失敗は、すべて応答に変えてから返す**という約束になります。

### HTML は純粋な関数で作る

HTML を作る処理は、一覧を受け取って文字列を返す純粋な関数です。テストでは、値がエスケープされることも確かめます。

```typescript
  it("escapes every value taken from the list", () => {
    const html = renderPage({
      listName: { name: `<b> & "q" 'a'` },
      items: [{ description: "<script>alert(1)</script>" }]
    })

    expect(html).toContain("<h1>&lt;b&gt; &amp; &quot;q&quot; &#39;a&#39;</h1>")
    expect(html).toContain("<li>&lt;script&gt;alert(1)&lt;/script&gt;</li>")
    expect(html).not.toContain("<script>")
  })
```

値をそのまま埋め込む仮の実装では、このテストが失敗します。エスケープを加えた実装は、次のとおりです。

```typescript
const entities: Readonly<Record<string, string>> = {
  "&": "&amp;",
  "<": "&lt;",
  ">": "&gt;",
  '"': "&quot;",
  "'": "&#39;"
}

const escapeHtml = (text: string): string =>
  text.replace(/[&<>"']/g, (character) => entities[character] ?? character)

export const renderPage = (list: ToDoList): string => {
  const items = list.items.map((item) => `<li>${escapeHtml(item.description)}</li>`).join("")
  const head = `<head><meta charset="utf-8"><title>Zettai</title></head>`
  const body = `<body><h1>${escapeHtml(list.listName.name)}</h1><ul>${items}</ul></body>`
  return `<!doctype html><html>${head}${body}</html>`
}
```

TypeScript の型は、エスケープ済みの文字列と、していない文字列を区別しません。どちらも `string` です。**値を埋め込む箇所でエスケープすることは、テストと規約で守ります。**

## Zettaiの開発を始める

ドメインの型は、読み取りに必要なものだけを定義します。

```typescript
export interface User {
  readonly name: string
}

export interface ListName {
  readonly name: string
}

export interface ToDoItem {
  readonly description: string
}

export interface ToDoList {
  readonly listName: ListName
  readonly items: ReadonlyArray<ToDoItem>
}
```

`readonly` は、型検査のうえで書き換えを禁じます。実行時には何もしません。

### 型の形が同じなら、同じ型になる

`User` と `ListName` は、別の名前を持つ型です。しかし、中身はどちらも `name: string` だけです。次のように、引数を取り違えた関数を書いて、型検査にかけました。

<!-- code-check: ignore わざと壊したコード -->
```typescript
export const swapped = (fetch: ToDoListFetcher, user: User, listName: ListName) =>
  fetch(listName, user)
```

**型検査も、lint も通りました。** TypeScript の型付けは**構造的**です。型の名前ではなく形を比べるので、同じ形の `User` と `ListName` は、互いに代わりになります。型の名前を分けたことは、読む人への説明にはなりますが、取り違えは止めません。この区別は、第 4 章で扱います。

### リストを見つける関数

利用者とリスト名からリストを探す関数の型を、`ToDoListFetcher` と名付けます。見つからないことがあるので、戻り値は Effect の `Option` です。テストは 3 本書きます。

```typescript
  it("finds a list by the names, not by the object identity", () => {
    expect(fetch({ name: "uberto" }, { name: "book" })).toEqual(Option.some(book))
  })

  it("returns none for an unknown list", () => {
    expect(fetch({ name: "uberto" }, { name: "music" })).toEqual(Option.none())
  })

  it("returns none for an unknown user", () => {
    expect(fetch({ name: "somebody" }, { name: "book" })).toEqual(Option.none())
  })
```

常に `Option.none()` を返す仮の実装では、1 本目だけが失敗し、残りの 2 本は通ります。「見つからない」ことを確かめるテストは、何も見つけない実装でも成り立ちます。

実装は次のとおりです。

```typescript
export type ToDoListFetcher = (user: User, listName: ListName) => Option.Option<ToDoList>

// キーはオブジェクトではなく、利用者の名前の文字列です。Map はオブジェクトのキーを参照で比べます。
export type ToDoListStore = ReadonlyMap<string, ReadonlyArray<ToDoList>>

export const findToDoList =
  (store: ToDoListStore): ToDoListFetcher =>
  (user, listName) =>
    Option.fromUndefinedOr(
      store.get(user.name)?.find((list) => list.listName.name === listName.name)
    )
```

`findToDoList` は、保管場所を受け取って `ToDoListFetcher` を返す関数です。呼び出す側は、保管場所がどこにあるかを知りません。

### オブジェクトを Map のキーにしない

コメントに書いた点は、型検査では見つかりません。キーを `User` にして、名前をオブジェクトのまま比べる実装を、一時的に作って確かめました。

<!-- code-check: ignore わざと壊したコード -->
```typescript
export type ObjectKeyStore = ReadonlyMap<User, ReadonlyArray<ToDoList>>

export const findByObjectKey = (store: ObjectKeyStore) => (user: User, listName: ListName) =>
  Option.fromUndefinedOr(store.get(user)?.find((list) => list.listName === listName))
```

**型検査と lint は通り、テストだけが失敗しました。** 内容が同じでも、別に作ったオブジェクトでは見つかりません（抜粋）。

```bash
AssertionError: expected { _id: 'Option', _tag: 'None' } to deeply equal { _id: 'Option', _tag: 'Some', …(1) }
```

JavaScript の `Map` と `===` は、オブジェクトを参照で比べます。テストで、保管場所に入れたものとは**別に作った**値を使って探すことが、この誤りを見つける条件です。

## 矢印で設計する

要求から応答までを、1 つの大きな関数にせず、小さな関数の連なりとして設計します。矢印は、関数の入力から出力への向きです。

```text
Request  ──→  ListRequest  ──→  ToDoList  ──→  string(HTML)  ──→  Response
        取り出す          探す            描く              応答にする
```

途中の矢印は失敗しうるので、失敗も型にします。この章の失敗は 2 つです。

```typescript
export type Failure = "bad-request" | "not-found"
```

### サーバーを起動せずにテストする

`App` は `Request` を受け取る関数なので、サーバーを起動しなくてもテストできます。

```typescript
// サーバーは起動しません。Request を渡して、Response を受け取ります。
const call = async (path: string, method = "GET") => {
  const response = await Effect.runPromise(app(new Request(`http://localhost${path}`, { method })))
  return { status: response.status, body: await response.text() }
}
```

テストは、一覧、日本語と空白を含む名前、404 になる要求、壊れた URL エンコードを合わせて 13 件です。ここで、常に 404 を返す仮の実装に対して、HTML のテスト 2 件と合わせて実行しました。

```bash
      Tests  3 failed | 12 passed (15)
```

**15 件のうち 12 件が通りました。** 3 件の失敗は、一覧を返すテスト、日本語の名前のテスト、400 を期待するテストです。「404 を返す」テストは大切ですが、それだけでは、何もしない実装と区別できません。

### 矢印を 1 本ずつ書く

最初の矢印は、`Request` から利用者とリスト名を取り出します。

```typescript
const decodeSegment = Option.liftThrowable(decodeURIComponent)

// 矢印 1: Request から、利用者とリスト名を取り出します。
export const extractListRequest = (request: Request): Result.Result<ListRequest, Failure> => {
  const [, root, user, listName, ...rest] = new URL(request.url).pathname.split("/")
  if (request.method !== "GET" || root !== "todo" || !user || !listName || rest.length > 0) {
    return Result.fail("not-found")
  }
  return Option.match(Option.all([decodeSegment(user), decodeSegment(listName)]), {
    onNone: () => Result.fail("bad-request"),
    onSome: ([userName, name]) => Result.succeed({ user: { name: userName }, listName: { name } })
  })
}
```

`decodeURIComponent` は、壊れた入力に例外を投げます。`Option.liftThrowable` は、例外を投げる関数を、失敗を `Option` で返す関数に変えます。投げられた例外の内容は捨てられます。ここでは「壊れている」ことだけが分かれば足ります。

残りの矢印と、組み立ては次のとおりです。

```typescript
// 矢印 2: 利用者とリスト名から、リストを取り出します。
const fetchList =
  (fetch: ToDoListFetcher) =>
  ({ user, listName }: ListRequest): Result.Result<ToDoList, Failure> =>
    Result.fromOption(fetch(user, listName), (): Failure => "not-found")

// 矢印 3: HTML と失敗を、それぞれ Response にします。
const htmlResponse = (html: string): Response =>
  new Response(html, { headers: { "content-type": "text/html; charset=utf-8" } })

const failureResponse = (failure: Failure): Response =>
  failure === "bad-request"
    ? new Response("bad request", { status: 400 })
    : new Response("not found", { status: 404 })

export const makeApp =
  (fetch: ToDoListFetcher): App =>
  (request) =>
    Effect.sync(() =>
      extractListRequest(request).pipe(
        Result.flatMap(fetchList(fetch)),
        Result.map(renderPage),
        Result.match({ onFailure: failureResponse, onSuccess: htmlResponse })
      )
    )
```

`makeApp` の中が、設計の図をそのまま表します。

- `Result.flatMap`: 失敗しうる次の矢印をつなぎます。前が失敗していれば、次は実行しません
- `Result.map`: 失敗しない関数（`renderPage`）を、成功の値に適用します
- `Result.match`: 最後に、成功と失敗の両方を `Response` にします

`Result.match` は、2 つの場合の両方を要求します。失敗の処理を書かないと `Property 'onFailure' is missing` の型エラーになり、`Result` のまま返しても型が合いません（どちらも壊して確かめました）。一方で、`failureResponse` の中の分岐は、2 つ目の分岐が「それ以外」を受ける形です。`Failure` に種類を 1 つ足しても、**型検査は通りました。** 足した種類は、404 になります。種類ごとに漏れなく分ける書き方は、第 6 章で扱います。

## ToDoリストをマップで提供する

最後に、`App` を Node のサーバーにつなぎ、`Map` に置いたリストを実際に提供します。ここは、型の外にある `node:http` の世界との境目です。

### 素朴な接続は、何を間違えるか

接続のテストでは、受け取ったパスをそのまま本文にする `App` を使い、接続の部分だけを確かめます。確かめるのは、停止、使用中のポート、パスの読み方、`GET` 以外のメソッド、そして例外が起きたときの応答です。

最初に書いた素朴な接続は、`incoming.url` と `incoming.method` から `Request` を作るだけのものでした。

<!-- code-check: ignore Red を確かめるための誤った実装 -->
```typescript
const url = new URL(incoming.url ?? "/", "http://localhost")
const request = new Request(url, { method: incoming.method ?? "GET" })
```

接続と外側のテスト 8 件のうち、6 件が失敗しました。

| テスト | 素朴な接続の結果 |
| :--- | :--- |
| `//todo/uberto/book` を送る | パスが `/uberto/book` になる。`//` で始まる文字列は、`base` と組み合わせると `todo` がホスト名になる |
| `TRACE` を送る | `new Request` が `'TRACE' HTTP method is unsupported.` を投げる。**応答が返らず、テストがタイムアウトした** |
| 例外を起こす `App` | 500 の本文に `Error: password=secret` が出る |
| `POST` を送る | `GET` ではないのに、`App` まで届いて 200 になる |
| スコープを閉じる（成功と失敗の 2 件） | サーバーが停止せず、要求が通り続ける |

**どれも型検査の対象外の誤りです。** この素朴な接続に戻し、停止の処理も外して確かめると、型検査も lint も通りました。`new URL` も `new Request` も、型のうえでは正しい呼び出しです。

### 接続を直す

要求を応答にする部分は、次のとおりです。

```typescript
// いまは GET と、本文の無い要求だけを扱います。本文の読み取りは、必要になる章で足します。
const toResponse = async (app: App, incoming: IncomingMessage): Promise<Response> => {
  const path = incoming.url ?? ""
  if (incoming.method !== "GET" || !path.startsWith("/")) {
    return new Response("not found", { status: 404 })
  }
  // パスは origin の後ろに連結します。第 2 引数を base にすると、// で始まるパスがホスト名になります。
  return Effect.runPromise(app(new Request(`http://localhost${path}`)))
}
```

```typescript
// Node の callback は Effect の外にあります。Effect を実行するのは、この最外周だけです。
const handle = (app: App) => (incoming: IncomingMessage, outgoing: ServerResponse) => {
  toResponse(app, incoming)
    .then((response) => write(response, outgoing))
    .catch(() => {
      // 失敗の理由は応答に出しません。記録は第 12 章で扱います。
      outgoing.writeHead(500)
      outgoing.end("internal server error")
    })
}
```

`catch` は、理由を応答にも、どこにも出していません。**いまの実装では、500 の原因は分かりません。** 記録は第 12 章で足します。

### 起動と停止を 1 組にする

起動に失敗する理由は、型にします。`Data.TaggedError` は、`_tag` を持つエラーの型を作ります。

```typescript
export class ServerError extends Data.TaggedError("ServerError")<{ readonly cause: unknown }> {}
```

`node:http` の起動と停止は、callback で終了を知らせます。`Effect.callback` は、callback を `Effect` にします。そして `Effect.acquireRelease` が、起動と停止を 1 組にします。

```typescript
// 起動と停止を 1 組にします。スコープを抜けるとき、成功でも失敗でも停止します。
export const serve = (app: App, port: number) =>
  Effect.acquireRelease(listen(app, port), close).pipe(
    Effect.map((server) => ({ port: (server.address() as AddressInfo).port }))
  )
```

`serve` を使う側は、`Effect.scoped` で**スコープ**を区切ります。スコープを抜けると `close` が実行されます。停止を呼び忘れない規律は要りません。使用中の処理が失敗しても停止することは、テストで確かめました。

`server.address() as AddressInfo` の `as` は、型検査を通すための宣言です。Node の型定義では、`address()` は文字列や `null` を返すこともあります。TCP のポートで待ち受けを始めた後に呼ぶので、ここでは `AddressInfo` です。**この判断は型検査されません。**

### domain の向きをテストで固定する

domain のコードは、HTTP を知らないようにします。domain から `web` を読み込むコードを一時的に作ると、**型検査と lint は通りました。** TypeScript は、どのディレクトリがどれを読み込んでよいかを知りません。そこで、domain のファイルが読み込む相手を、テストで確かめます。

```typescript
const domain = new URL("../src/domain/", import.meta.url)
const IMPORT = /(?:\bfrom|\bimport)\s*\(?\s*["']([^"']+)["']/g

// domain が使ってよいのは、effect の本体と、domain の中のファイルだけです。
const isAllowed = (specifier: string): boolean =>
  specifier === "effect" || /^\.\/[\w-]+\.ts$/.test(specifier)
```

違反を置くと、このテストが失敗します（抜粋）。

```bash
AssertionError: expected [ '../web/html.ts' ] to deeply equal []
```

これは、ソースの文字列を調べる簡易な検査です。`require` のような別の読み込み方や、文字列を組み立てて読み込むコードは、見つけません。

### 起動する

起動の入口では、ポート番号を環境変数 `ZETTAI_PORT` で変えられます。指定が無ければ 8080 番です。値の検査は、第 1 章と同じく `Result` を返す純粋な関数にしました。

```typescript
const program = Effect.gen(function* () {
  const port = yield* Effect.fromResult(parsePort(process.env["ZETTAI_PORT"]))
  const server = yield* serve(makeApp(findToDoList(sampleLists)), port)
  yield* Effect.sync(() => {
    console.log(`http://127.0.0.1:${server.port}/todo/uberto/book`)
  })
  return yield* Effect.never
})

// アプリケーションで Effect を実行するのは、ここと、server.ts の要求ごとの実行だけです。
await Effect.runPromise(Effect.scoped(program))
```

ポートを変えられるようにしたのは、実際に起動に失敗したためです。確かめた開発機（Windows）では、8080 番の待ち受けが `EACCES` で失敗し、8080 番では別のプログラムが応答していました。

```bash
cd apps/typescript-fp/zettai/zettai-step2-http-domain
ZETTAI_PORT=18123 npm run start
```

`http://127.0.0.1:18123/todo/uberto/book` に要求を送ると、次の HTML が返ります。

```bash
<!doctype html><html><head><meta charset="utf-8"><title>Zettai</title></head><body><h1>book</h1><ul><li>write chapter</li><li>insert code</li><li>publish book</li></ul></body></html>
```

終了は `Ctrl+C` です。シグナルを受けて停止の処理を実行する仕組みは入れていません。このとき `close` が実行されるかは、確かめていません。

品質ゲートの smoke は、ビルドした出力でサーバーを起動し、要求を 1 回送って停止します。これで、冒頭の外側のテストと、ビルドした出力の両方で、縦串が通りました。

## まとめ

この章では、最小の縦串を通し、次を確かめました。

- HTTP は `Request` から `Effect<Response>` への関数として書け、サーバーを起動せずにテストできる
- 要求から応答までは、小さな矢印を `Result` でつないで組み立てる
- `Effect.acquireRelease` とスコープで、サーバーの停止を呼び忘れない形にできる
- 素朴な接続の実装には、型検査を通る誤りがあり、実際の要求を送るテストが見つけた

`npm run check` は、第 1 章の step と合わせて、テスト 14 ファイル 52 件が通ります（2026-11-27、Windows、Node 22.23.4）。`nixos/nix` のコンテナ（Linux、Node 24.12.0）では、型検査、lint、format、smoke と、PostgreSQL のテストを除く 51 件が通りました。GitHub Actions（`ubuntu-latest`、Node 24.22.3）では、PostgreSQL のテストを含む 52 件と smoke が通りました。次のことは確かめていません。性能、同時の要求、大きな要求、停止を始めた時点で処理中の要求の扱いです。

### 型で守れたこと・守れなかったこと

| 対象 | 型検査 | 守ったもの |
| :--- | :--- | :--- |
| 失敗を応答に変え忘れる | 止める（`Result.match` が両方を要求する） | 型 |
| 失敗の種類を増やして、応答を書き忘れる | **通る** | 無い（第 6 章で扱う） |
| `User` と `ListName` の取り違え | **通る** | 無い（第 4 章で扱う） |
| `Map` のキーをオブジェクトにする | **通る** | テスト |
| domain から web を読み込む | **通る** | 境界のテスト |
| エスケープしていない値を HTML に入れる | **通る** | テスト |
| 素朴な接続の誤り（パス、`TRACE`、停止、500 の本文） | **通る** | 実際のサーバーに要求を送るテスト |

### Effectに置き換えたこと・自作で残したこと

| 対象 | この章での扱い |
| :--- | :--- |
| 見つからないことがある値 | Effect の `Option` を使う |
| 失敗しうる矢印の合成 | Effect の `Result` と `pipe` を使う |
| 起動と停止の対 | `Effect.acquireRelease` と `Scope` を使う |
| callback を持つ API | `Effect.callback` で `Effect` にする |
| ルーティング、`node:http` への接続、HTML のエスケープ | 自作する。`effect/http` と `@effect/platform-node` は使わない（[ADR-056](../../../adr/ADR-056-typescript-http-with-stable-parts.md)） |

次の章では、同じ受け入れシナリオを、domain を直接呼ぶ経路と、HTTP を通る経路の両方で実行できるようにします。

## この章で書いたコード

- `apps/typescript-fp/zettai/zettai-step2-http-domain/src/domain/todo-list.ts`
- `apps/typescript-fp/zettai/zettai-step2-http-domain/src/domain/find-todo-list.ts`
- `apps/typescript-fp/zettai/zettai-step2-http-domain/src/web/html.ts`
- `apps/typescript-fp/zettai/zettai-step2-http-domain/src/web/app.ts`
- `apps/typescript-fp/zettai/zettai-step2-http-domain/src/web/server.ts`
- `apps/typescript-fp/zettai/zettai-step2-http-domain/src/application/`（`port.ts`、`sample-lists.ts`）
- `apps/typescript-fp/zettai/zettai-step2-http-domain/src/`（`main.ts`、`smoke.ts`）
- `apps/typescript-fp/zettai/zettai-step2-http-domain/test/`

## 参照

- Uberto Barbini『From Objects to Functions』第 2 章。章の題材と順序は本書に拠っています。本連載のコードはすべて書き起こした自作実装です
- [Node.js の `http` モジュール](https://nodejs.org/api/http.html)
- [Fetch Standard](https://fetch.spec.whatwg.org/)（`Request` と `Response`）
- [Effect 公式サイト](https://effect.website/)
