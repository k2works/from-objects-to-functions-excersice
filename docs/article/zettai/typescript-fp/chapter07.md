---
type: Article
title: "第 7 章 関数型手法によるエラーハンドリング"
description: "Zettai 連載 TypeScript FP 版の第 7 章。失敗の種類を足しても型検査が通っていた穴を塞ぎ、取得の失敗を型に出す。ファンクタの法則を実装を引数にしたテストで確かめ、見分けられない壊し方も示す。業務の失敗だけを型で運び、想定外は defect にする線を決める。"
tags: [article, zettai, typescript, effect, error-handling, functor, chapter]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-28T04:52:33Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-28T06:55:54Z }
---

# 第 7 章 関数型手法によるエラーハンドリング

第 6 章で、コマンドを断る理由が型に現れました。しかし、理由は画面に届きません。リストの取得は、まだ「あるか無いか」です。この章では、**失敗を型で運び、HTTP の応答に漏れなく対応させます。** そして、型で運ぶ失敗と、運ばない失敗の線を決めます。

この章のコードは `zettai-step5-errors` にあります。第 6 章の step を複製して始めました。`effect` の版は 4.1.6、性質テストの道具は fast-check 4.11.0 です。

## より適切なエラー処理

### 失敗の種類を足しても、型検査は通っていた

第 6 章までの web は、失敗を次のように応答にしていました。

<!-- code-check: ignore 第 6 章までの step のコード -->
```typescript
const failureResponse = (failure: Failure): Response =>
  failure === "bad-request"
    ? new Response("bad request", { status: 400 })
    : new Response("not found", { status: 404 })
```

三項演算子は、「400 でないものは、すべて 404」と書いています。失敗の種類を 1 つ足して確かめると、**型検査も、その step のテスト 114 件も通りました。** 新しい失敗は、黙って 404 になります。

### 種類ごとの表にする

失敗の種類ごとに、状態を並べます。

```typescript
// 失敗の種類ごとの状態です。種類を足すと、キーが足りないので型検査が失敗します。
const statusOf: Record<Failure, 400 | 404> = {
  BadRequest: 400,
  UnknownPath: 404,
  ListNotFound: 404,
  ItemNotFound: 404,
  ListAlreadyExists: 400,
  InvalidTransition: 400
}
```

`Record<Failure, …>` は、`Failure` の種類の分だけキーが要ります。失敗を 1 つ足すと、次のように止まります。

```bash
error TS2741: Property 'ListArchived' is missing in type '{ BadRequest: 400; UnknownPath: 404; ListNotFound: 404; ItemNotFound: 404; ListAlreadyExists: 400; InvalidTransition: 400 }'
```

型が守るのは、**書き漏れ**だけです。状態の値の誤りは止まりません。`InvalidTransition` を 404 に変えると、型検査は通り、テストが失敗しました。

```typescript
// HTTP の要求そのものの失敗です。domain の失敗とは分けます。
export type RequestFailure = "BadRequest" | "UnknownPath"

export type Failure = ZettaiError["_tag"] | RequestFailure
```

`ZettaiError["_tag"]` は、失敗の型の和から、種類の名前だけを取り出した型です。domain で失敗を足すと、web の表にも自動で影響します。

### 本文に、中身を出さない

失敗の値は、利用者の名前やリストの名前を持っています。応答の本文は、状態ごとの固定の文字列にします。

```typescript
// 本文は状態ごとの固定の文字列です。失敗の中身は出しません。
const bodies = { 400: "bad request", 404: "not found" } as const

export const failureResponse = (failure: Failure): Response =>
  new Response(bodies[statusOf[failure]], { status: statusOf[failure] })
```

`failureResponse` は、失敗の値ではなく、**種類の名前だけを受け取ります。** 中身が本文に混ざる道を、引数の型で狭めています。ただし、本文に種類の名前を出すように壊すと、型検査は通り、テストが失敗しました。

いまの画面にあるのは取得だけです。HTTP で起こる業務の失敗は `ListNotFound` だけで、400 になる 2 つは、対応の関数を直接呼んで確かめています。

## ファンクタと圏を学ぶ

`Result` には `map` があります。成功の値に関数を適用し、失敗はそのまま通します。このような「中身に関数を適用して、形を保つ」操作を持つものを**ファンクタ**と呼びます。

圏は、対象と、対象から対象への矢印、矢印の合成、何もしない矢印からなります。ここでは、**型が対象、関数が矢印**です。ファンクタは、型を別の型（`number` を `Result<number, string>` に）、関数を別の関数（`map` を掛けた関数）に移します。このとき、次の 2 つを保つ必要があります。

- **恒等則:** 何もしない関数を `map` しても、何も変わらない
- **合成則:** `f` を `map` してから `g` を `map` しても、`g` と `f` を合成してから 1 回 `map` しても、同じ

### 法則を、実装を引数にしたテストにする

法則のテストは、`map` の実装を受け取る形にします。**壊した `map` にも、同じ法則を当てられます。** 試行回数は 500 回です。

```typescript
const resultIdentity = (map: ResultMap) =>
  fc.property(outcome, (value) =>
    isDeepStrictEqual(
      map(value, (n) => n),
      value
    )
  )
```

生成する関数は、名前を持つ 3 つから選びます。反例に名前が表示されます。

```typescript
// 反例が読めるように、名前を持つ関数から選びます。順序で結果が変わる組を含めます。
const fn = fc.constantFrom<Fn>(
  { name: "add 1", run: (n) => n + 1 },
  { name: "double", run: (n) => n * 2 },
  { name: "negate", run: (n) => -n }
)
```

本物の `map` を、関数を 2 回当てる `map` に置き換えると、合成則が次の反例で失敗しました。

```bash
Error: Property failed after 7 tests
Counterexample: [success(0),{"name":"add 1","run":(n) => n + 1},{"name":"double","run":(n) => n * 2}]
```

「`0` に、1 を足してから 2 倍する」で食い違う、と読めます。

### 法則ごとに、見分けられるものが違う

| 壊した `map` | 恒等則 | 合成則 |
| :--- | :--- | :--- |
| 成功の値に、関数を 2 回当てる | **通る** | 失敗する |
| 失敗を、成功の 0 にする | 失敗する | この版では確かめていない |
| 渡された関数を使わない | **通る** | **通る** |

1 行目があるので、恒等則だけでは足りません。

3 行目は、この章のテストの限界です。**`number` から `number` への関数だけで試すと、関数を使わない実装も、2 つの法則を両方満たします。**

```typescript
  // number から number の map だけを試すと、関数を無視する実装も、両方の法則を満たします。
  it("cannot be told by these laws from a map that ignores the function", () => {
```

`map` が本来持つ型は、値の型を変える関数も受け取ります。型を変える関数を渡せば、使わない実装は型が合いません。一方、この版のテストは型を固定しているので、型の側の守りが効きません。**法則のテストは、「正しい `map` である」ことの証明ではありません。**

## ファンクタを使ったエラーハンドリング

### Effect の map は、値を比べるだけでは足りない

`Effect` にも `map` があります。`Effect` は「実行するまで何も起きない値」なので、値のままでは比べられません。**実行して、結果と、何が実行されたかの記録を比べます。**

```typescript
// Effect は値のまま比べられません。実行して、結果と、何が実行されたかの記録を比べます。
const observe = async (value: Result.Result<number, string>, build: (source: Source) => Source) => {
  const log: Array<string> = []
  const source: Source = Effect.suspend(() => {
    log.push("source")
    return Effect.fromResult(value)
  })
  return { exit: await Effect.runPromiseExit(build(source)), log }
}
```

記録が要る理由は、次の壊し方で分かります。

```typescript
// 元の Effect を 2 回実行します。返す値は正しいままです。
const runsTwice: EffectMap = (self, f) =>
  self.pipe(
    Effect.flatMap(() => self),
    Effect.map(f)
  )
```

**この `map` は、結果だけを比べると恒等則を満たします。** 実行の記録も比べると、失敗します。元の `Effect` が保存だった場合、2 回保存することになります。

### 失敗は、後ろの処理を飛ばす

`map` と `flatMap` でつないだ処理は、失敗すると後ろを実行しません。成功の場合の処理だけを順に書けば、失敗は最後まで運ばれます。取得では、次のように使います。

```typescript
const showList =
  (hub: ToDoListHub) =>
  ({ user, listName }: ListRequest): Effect.Effect<Response> =>
    hub.getList(user, listName).pipe(
      Effect.map((list) => htmlResponse(renderPage(list))),
      Effect.catch((error) => Effect.succeed(failureResponse(error._tag)))
    )
```

`Effect.map` で成功の値を画面にし、`Effect.catch` で型付きの失敗を応答にします。戻り値の型は `Effect<Response>` で、エラーの型引数がありません。**すべての型付きの失敗を応答にしたことが、型に現れます。**

## `Result` と `Effect` の型付きエラーで実装する

### 取得の失敗を、型に出す

第 6 章まで、取得は `Option` でした。無い理由は、web が推測していました。ハブの `getList` の型を変えます。

```typescript
  // リストが無いことは、型に現れる失敗です。
  readonly getList: (user: User, listName: ListName) => Effect.Effect<ToDoList, ListNotFound>
```

アダプタの型は変えません。アダプタは「あるか無いか」を返し、**失敗の理由は domain が付けます。**

```typescript
  // アダプタは「あるか無いか」を返します。失敗の理由を付けるのは、domain です。
  getList: (user, listName) =>
    fetchList(user, listName).pipe(
      Effect.flatMap((found) =>
        Effect.fromResult(Result.fromOption(found, () => listNotFound(user, listName)))
      )
    ),
```

`E` に `ListNotFound` だけを書いているので、呼び出し側は、ほかの失敗を扱う必要がないと型から分かります。

この変更で、第 6 章の step から変わったファイルは、`src/` が 4、既存のテストが 3 でした。出来事、状態の畳み込み、状態の移り方は変わっていません。

### Result と、E の使い分け

この版は、次のように分けます（[ADR-061](../../../adr/ADR-061-typescript-hub-ports-and-error-types.md)）。

| 場所 | 型 | 理由 |
| :--- | :--- | :--- |
| 核（`handle` など） | `Result<A, E>` | 実行を伴わない。呼べば結果が出て、値として比べられる |
| 保管を読み書きする境界（ハブ） | `Effect<A, E>` | 実行するまで起きない。失敗も、実行して初めて分かる |
| 2 つのつなぎ目 | `Effect.fromResult` | `Result` の失敗が、`E` の失敗になる |

失敗の値は、`_tag` を持つ素の型の和です。Effect の `Data.TaggedError` を使うと、`Error` のインスタンスになってスタックを持ち、`Effect.gen` の中で `yield*` だけで失敗にできます。`catchTag` はどちらの形でも使えました。この章ではその利点を使わないので、イベントやコマンドと同じ形にそろえました。

### 型で運ぶ失敗と、運ばない失敗

`Effect` には、失敗が 2 種類あります。型の `E` に現れる**型付きの失敗**と、型に現れない **defect** です。Effect 4.1.6 で確かめると、次のようになりました。

| 書き方 | 種類 |
| :--- | :--- |
| `Effect.fail`、`Effect.fromResult` の失敗 | 型付きの失敗 |
| `Effect.die` | defect |
| `Effect.sync` の中の `throw`、`Result.getOrThrow` | defect |

`Effect.catch` は、defect を捕まえません。

どれを型で運ぶかを、3 通りで比べました。

| 項目 | A: 業務の失敗だけを型に | B: 想定外も包んで型に | C: 業務の失敗も例外に |
| :--- | :--- | :--- | :--- |
| コマンドの実行の型に現れる失敗 | `ZettaiError` | `ZettaiError` と、包んだ型 | 何も現れない |
| すべてを捕まえる `Effect.catch` を掛けると | 拒否だけを捕まえる | **保存の失敗と、実装の誤りも捕まえる** | 何も捕まえない |
| 失敗の種類を足すと | HTTP の対応で型検査が止まる | 同じ | **型検査が通る** |
| 比較用の実装の行数 | 27 | 35 | 33 |

**この版は A にします**（[ADR-060](../../../adr/ADR-060-typescript-typed-errors-and-defects.md)）。業務の上で起こりうる失敗だけを型で運び、保管の故障や実装の誤りは defect にします。B では、失敗をまとめて扱う 1 行が、実装の誤りまで業務の失敗と同じに扱います。C では、起こりうる失敗が型から読めません。

defect は、ハブも web も捕まえません。最外周の `node:http` への接続が、理由を出さずに 500 を返します。

```typescript
  it.each([
    ["a defect of the adapter", () => Effect.die(new Error("password=secret"))],
```

線が守られていることは、テストで固定します。web にすべての原因を捕まえる処理を足すように壊すと、型検査は通り、**この 500 のテストが 2 件失敗しました。** 接続が理由を本文に出すように壊しても、同じく型検査は通り、テストが失敗しました。

500 の原因は、いまは記録していません。後から原因を追えません。記録は第 12 章で扱います。データベースの失敗を扱う第 9 章で、この線をもう一度確かめます。

## まとめ

この章では、次を確かめました。

- 失敗の種類と状態の対応を `Record` にすると、書き漏れは型で止まる。値の誤りは止まらない
- `map` の法則は、2 つそろえる。それでも、型を固定したテストでは見分けられない実装がある
- `Effect` の法則は、結果だけでなく、実行の記録も比べる
- 核は `Result`、保管を読み書きする境界は `Effect` の `E` にする
- 業務の失敗だけを型で運び、想定外は defect にする

`npm run check` は、テスト 78 ファイル 540 件が通ります（2026-11-28、Windows、Node 22.23.4）。

次の章では、イベントから、画面に表示するための値を作ります。読み取りと書き込みで、形を分けます。

### 型で守れたこと・守れなかったこと

| 対象 | 型検査 | 守ったもの |
| :--- | :--- | :--- |
| 失敗の種類を足して、状態の対応を書かない | **止める** | `Record` の型 |
| 状態の値を誤る | **通る** | 対応のテスト |
| 応答の本文に、失敗の情報を出す | **通る** | 本文のテスト |
| `ListNotFound` を扱わずに、`Effect<Response>` にする | **止める** | `E` の型（戻り値の型を書いた場合） |
| web が、すべての原因を捕まえる | **通る** | 500 のテスト |
| 接続が、defect の理由を応答に出す | **通る** | 500 のテスト |
| `map` の法則を壊す | **通る** | 性質テスト（見分けられない壊し方がある） |

### Effectに置き換えたこと・自作で残したこと

| 対象 | この章での扱い |
| :--- | :--- |
| 成功か失敗かの型と、ファンクタ | Effect の `Result` と `Effect` の `map` を使う。自作のファンクタは作らず、法則をテストで確かめる |
| 型付きの失敗と defect の区別 | Effect の仕組みを使う |
| 失敗の値の型 | 自作する（`_tag` を持つ型の和）。`Data.TaggedError` は、比べて採らなかった |
| HTTP の状態との対応 | 自作する |

## この章で書いたコード

- `apps/typescript-fp/zettai/zettai-step5-errors/src/domain/`（`errors.ts`、`handle.ts`、`hub.ts`）
- `apps/typescript-fp/zettai/zettai-step5-errors/src/web/app.ts`
- `apps/typescript-fp/zettai/zettai-step5-errors/test/`（`errors.test.ts`、`functor-laws.test.ts`）

## 参照

- Uberto Barbini『From Objects to Functions』第 7 章。章の題材と順序、ファンクタを使って失敗を扱う考え方は本書に拠っています。本連載のコードはすべて書き起こした自作実装です。原著が作る `Outcome` は、この版では作らず、Effect の `Result` を使います
- [Effect の公式サイト](https://effect.website/)。この章の API は、版 4.1.6 の型定義と、テストの実行で確かめました
- [fast-check の公式ドキュメント](https://fast-check.dev/)
