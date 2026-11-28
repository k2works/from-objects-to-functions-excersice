---
type: Article
title: "第 4 章 ドメインとアダプタのモデリング"
description: "Zettai 連載 TypeScript FP 版の第 4 章。ドメインの入口をハブにまとめ、依存の渡し方を引数・Context.Service・Layer の 3 通りで比べる。矢印を単独で呼んで調べる手順を示し、Brand で利用者とリスト名を区別する。目印が実行時に残らず、as や JSON.parse では守れないことも確かめる。"
tags: [article, zettai, typescript, effect, domain-model, chapter]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-28T01:06:22Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-28T01:21:38Z }
---

# 第 4 章 ドメインとアダプタのモデリング

第 3 章までで、リストを見る縦串と、2 経路の受け入れテストがそろいました。この章では、リストを**変える**準備をします。ドメインの入口を 1 つにし、依存の渡し方を決め、利用者の名前とリストの名前を型で区別します。

この章のコードは、第 3 章の step を複製した `zettai-step3-events` にあります。`effect` の版は 4.1.6 です。

## ToDoリストを変更するための新しいストーリーを始める

次に利用者ができるようにしたいのは、自分のリストを作り、項目を足し、項目の進み具合を変えることです。

- 利用者は、新しいリストを作れる
- 利用者は、リストに項目を足せる
- 利用者は、項目の状態と期限を扱える

この章では、まだ何も変更しません。変更を足すと、domain の型と、domain が外に頼るものが増えます。増える前に、次の 3 つを整えます。

1. web が domain を使う入口を 1 つにする
2. その入口に、依存をどう渡すかを決める
3. 取り違えたくない値を、型で区別する

リストの作成と項目の追加は第 5 章で、状態の遷移は第 6 章で実装します。

## 関数型の「依存性の注入」を使う

第 3 章まで、web の `makeApp` は、リストを取得する関数を直接受け取っていました。変更の操作が増えると、web が受け取る関数も増え、web が domain の内側の部品を知ることになります。

そこで、domain の入口を 1 つ作ります。**ハブ**（`ToDoListHub`）と呼びます。

```typescript
// ドメインの入口です。web は、この型の操作だけを呼びます。
export interface ToDoListHub {
  readonly getList: ToDoListFetcher
}

// 依存は、関数の引数で受け取ります。
export const makeHub = (fetch: ToDoListFetcher): ToDoListHub => ({ getList: fetch })
```

いまの操作は 1 つなので、ハブは取得の関数をそのまま公開するだけです。ハブに意味が出るのは、操作と依存が増える第 6 章からです。

web は、ハブだけを受け取ります。

```typescript
export const makeApp =
  (hub: ToDoListHub): App =>
  (request) =>
```

組み立てるのは、起動の入口とテストです。

```typescript
  const server = yield* serve(makeApp(makeHub(findToDoList(sampleLists))), port)
```

テストでは、保管もサーバーも使わずに、好きな関数を渡せます。

```typescript
    const hub = makeHub((_user, listName) =>
      Option.some({ listName, items: [makeToDoItem("from the test")] })
    )

    const response = await get(makeApp(hub), "/todo/anyone/anything")
```

「依存性の注入」という名前がついていますが、していることは、**関数を引数に渡す**ことだけです。専用の仕組みは要りません。

### web がハブだけを知ることを固定する

型だけでは、web が取得の依存を知らないことは保てません。web のファイルが `find-todo-list.ts` を読み込んでも、型検査は通るからです。第 2 章の domain の境界と同じく、読み込みを調べるテストを書きます。

```typescript
// web が使ってよい domain のファイルは、ハブと型だけです。取得の依存は、ハブの内側にあります。
const ALLOWED_FOR_WEB = ["../domain/hub.ts", "../domain/todo-list.ts"]
```

ハブを入れる前の `app.ts` は、このテストで失敗しました。

```bash
AssertionError: expected [ '../domain/find-todo-list.ts' ] to deeply equal []
```

渡し忘れと、ハブの代わりに取得の依存を渡す誤りは、型のテストで固定します。`@ts-expect-error` は、次の行が型エラーになることを求める指定です。エラーが無くなると、型検査が失敗します。

```typescript
  // @ts-expect-error 取得の依存を渡さないと、ハブを作れません
  makeHub()
  // @ts-expect-error ハブを渡さないと、App を作れません
  makeApp()
  // @ts-expect-error web が受け取るのはハブです。取得の依存は受け取りません
  makeApp(findToDoList(new Map()))
```

### Effect の R に載せる形と比べる

Effect には、依存を型に載せる仕組みがあります。`Effect<A, E, R>` の 3 つ目の型引数 `R` は、**この `Effect` を実行するのに必要なもの**を表します。依存を `R` に載せると、渡し忘れた依存が型に残ります。そこで、同じ処理を 3 通りに書いて比べました。

| 案 | 依存の渡し方 |
| :--- | :--- |
| A | 関数の引数でハブに渡す |
| B | `Context.Service` にして、`Effect.provideService` で値を与える |
| C | `Context.Service` にして、`Layer` で与える |

B と C では、依存に名前を付けた型（service のキー）を作り、使う側はそれを `yield*` で取り出します。

<!-- code-check: ignore 比較に使った一時的なコード -->
```typescript
export class Fetcher extends Context.Service<Fetcher, ToDoListFetcher>()("zettai/Fetcher") {}

const getList = (user: User, listName: ListName) =>
  Effect.gen(function* () {
    const fetch = yield* Fetcher
    return fetch(user, listName)
  })
```

値を与えるのは、組み立てる側です。上が B、下が C です。

<!-- code-check: ignore 比較に使った一時的なコード -->
```typescript
app(request).pipe(Effect.provideService(Fetcher, fetch))

app(request).pipe(Effect.provide(Layer.succeed(Fetcher, findToDoList(store))))
```

3 案は、あるリスト、無いリスト、無い利用者に同じ応答を返し、テストで依存を差し替えられました。違いは次のとおりです（依存が 1 つの、小さな実装で比べた結果です）。

| 項目 | A | B | C |
| :--- | :--- | :--- | :--- |
| 行数（ハブ・web・組み立て・差し替え） | 35 | 43 | 41 |
| 渡し忘れ | 型検査で止まる | 型検査で止まる | 型検査で止まる |
| 依存を 1 つ増やしたとき、ハブのほかに直すファイル | 2 | 3 | 3 |

**渡し忘れは、3 案とも型検査で止まりました。** A では引数の数が足りないエラーになります。B と C では、Effect の診断が、足りない依存の名前を示します。

```bash
error TS377004: This Effect requires a service that is missing from the expected Effect context: `Fetcher`. effect(missingEffectContext)
```

ただし、B と C でエラーになるのは、`App` として返す、`runPromise` で実行する、サーバーに渡す、といった**境界に達した場所**です。まだ境界に渡していない関数は、`R` を持ったまま型検査を通ります。

B と C で直すファイルが 1 つ多いのは、web の戻り値の型に `R` を書いていたためです。型を推論に任せた場合は、確かめていません。C には、もう 1 つ注意点がありました。要求ごとに `Effect.provide(layer)` を呼ぶと、3 回の要求で `Layer` が 3 回組み立てられました。`Layer.build` で一度組み立ててから渡すと、1 回でした。接続のような資源を持つ `Layer` では、組み立てる場所を決める必要があります。B と C の形では、サーバーは起動していません。

**この版は A を選びました**（[ADR-058](../../../adr/ADR-058-typescript-dependency-by-function-argument.md)）。いまの依存は値が 1 つで、`R` に載せても、渡し忘れを見つける力に差が見られなかったためです。比べたのは依存が 1 つの場合だけです。接続の寿命を扱う第 9 章と、トランザクションを扱う第 10 章で、この選択をもう一度確かめます。

## 関数型コードをデバックする

関数をつないだコードでは、途中の値が変数に残りません。失敗したとき、どこから調べればよいでしょうか。

`makeApp` は、3 つの矢印をつないでいます。要求から名前を取り出し、名前からリストを取り出し、結果を応答にします。**このうち、名前を取り出すところで、利用者とリスト名をわざと取り違えました。** 型検査は通ります。生の文字列は、どちらの生成の関数にも渡せるからです。

<!-- code-check: ignore わざと壊したコード -->
```typescript
  Result.all({ user: parseUser(rawListName), listName: parseListName(rawUser) })
```

失敗したのは、HTTP を通るテストだけでした。domain を直接呼ぶテストと、受け入れテストの domain の経路は通ります。ここで、原因が web の側にあると分かります。第 3 章で経路を分けた効果です。

しかし、web のテストが示すのは、次の 1 行です。

```bash
AssertionError: expected 404 to be 200 // Object.is equality
```

404 は、パスが違う場合にも、名前にできない場合にも、リストが無い場合にも返ります。応答からは、どの矢印が原因か分かりません。

矢印は、普通の関数です。**入口の矢印を単独で呼んで、途中の値を比べます。** この版で外から単独で呼べるのは、公開している矢印 1 と、ハブの操作です。

```typescript
// 矢印 1 だけを呼びます。応答の 404 からは分からない、取り出した値を比べます。
it("takes the user and the list name from the path in this order", () => {
  const request = new Request("http://localhost/todo/uberto/book")

  expect(extractListRequest(request)).toEqual(
    Result.succeed({ user: userOf("uberto"), listName: listNameOf("book") })
  )
})
```

同じ壊し方で、このテストは取り違えをそのまま表示しました。

```bash
- Expected
+ Received

  {
    "_id": "Result",
    "_tag": "Success",
    "success": {
-     "listName": "book",
-     "user": "uberto",
+     "listName": "uberto",
+     "user": "book",
    },
  }
```

手順をまとめると、次のとおりです。

1. **どのテストが失敗し、どれが通るかを見る。** domain が通って HTTP が失敗するなら、web か接続を疑います
2. **失敗した側を、入口から順に、単独で呼んで確かめる。** 期待と違う値が最初に出た場所を調べます。公開していない矢印を確かめたいときは、公開するか、前後の値から絞ります
3. **調べるのに使った呼び出しを、テストとして残す。**

この手順が使えるのは、矢印が受け取った値だけで結果を決めるからです。矢印 1 を呼ぶのに、サーバーも保管も要りません。

## 関数型ドメインモデリング

### 同じ形の型は、区別されない

第 2 章で、`User` と `ListName` は次の形でした。

<!-- code-check: ignore 第 2〜3 章の step のコード -->
```typescript
export interface User {
  readonly name: string
}

export interface ListName {
  readonly name: string
}
```

TypeScript の型は、**名前ではなく形で区別します**（構造的型付け）。この 2 つは同じ形なので、利用者の位置にリスト名を渡しても、型検査は通ります。

先にテストを書きます。入れ替えて渡す行に `@ts-expect-error` を付けると、型検査は「指定が使われていない」と失敗します。エラーになるはずの行が、通っているためです。

```bash
error TS2578: Unused '@ts-expect-error' directive.
```

### Brand で区別する

Effect の `Brand` は、型にだけ目印を付けます。

```typescript
// 中身は同じ string ですが、型の上では別の型です。区別は型検査の中だけにあります。
export type User = Brand.Branded<string, "User">
export type ListName = Brand.Branded<string, "ListName">
export type DueDate = Brand.Branded<string, "DueDate">
```

`User` は「`string` で、`User` の目印があるもの」です。目印の違う型は、互いに渡せません。

```typescript
  // @ts-expect-error 利用者の位置に、リスト名は渡せません
  hub.getList(listName, listName)
  // @ts-expect-error リスト名の位置に、利用者は渡せません
  hub.getList(user, user)
```

2 つの向きは、別の行にします。1 行で両方を入れ替えると、片方だけがエラーになる場合でも、指定は満たされるためです。

`name` を持つオブジェクトをやめて、目印を付けた文字列にしました。実行時の値が文字列なので、`Map` のキーを中身で引けます。第 2 章で必要だった、キーにするための取り出しが無くなります。

```typescript
// User の実行時の値は文字列なので、Map は中身で引けます。
export type ToDoListStore = ReadonlyMap<User, ReadonlyArray<ToDoList>>

export const findToDoList =
  (store: ToDoListStore): ToDoListFetcher =>
  (user, listName) =>
    Option.fromUndefinedOr(store.get(user)?.find((list) => list.listName === listName))
```

### 名前は、検査する関数を通して作る

生の文字列は、そのままでは `User` になりません。作るときに検査します。この章の制約は「空でない」だけです。長さなどの制約は、第 11 章で足します。空白 1 文字の名前は、まだ通ります。

```typescript
const notEmpty = (raw: string) => raw.length > 0 || "名前が空です"
```

```typescript
// 生成の関数は公開しません。そのまま呼ぶと、失敗が例外になるためです。
const userBrand = Brand.make<User>(notEmpty)
const listNameBrand = Brand.make<ListName>(notEmpty)
```

`Brand.make` が返す生成の関数は、そのまま呼ぶと、検査に失敗したときに例外を投げます。`.result` を使うと、失敗が `Result` の値になります。この版では、`.result` を使う関数だけを公開します。

```typescript
export const parseUser = (raw: string): Result.Result<User, Brand.BrandError> =>
  userBrand.result(raw)
```

生の文字列を domain の型にするのは、HTTP の境界です。

```typescript
// 生の文字列を domain の型にします。名前にできない値の要求は、無いものとして扱います。
const toNames = (rawUser: string, rawListName: string): Result.Result<ListRequest, Failure> =>
  Result.all({ user: parseUser(rawUser), listName: parseListName(rawListName) }).pipe(
    Result.mapError((): Failure => "not-found")
  )
```

境界を通った後の関数は、名前が空かどうかを確かめ直しません。引数の型が `User` なら、検査を通った値だという約束です。ただし、次の節のとおり、この約束は破れます。

### Brand が守らないもの

目印は型検査の中だけにあります。実行時の `User` は、ただの文字列です。`typeof` は `string` を返します。次の書き方は、どれも**型検査と lint を通りました。**

<!-- code-check: ignore 型検査を通ることを確かめた書き方 -->
```typescript
  hub.getList("" as User, listName)
  const fromJson = JSON.parse('{"user": ""}')
  hub.getList(fromJson.user, listName)
  const plain: string = user
  const path = (owner: string, name: string) => `/todo/${owner}/${name}`
  return [plain, path(listName, user)]
```

- **`as` で型を言い切る:** 検査を飛ばして、空の利用者を作れます
- **`JSON.parse` の結果:** 型が `any` なので、検査せずに渡せます。外から来る値は、第 9 章と第 12 章でも境界で検査します
- **`string` を受け取る関数に渡す:** `User` は `string` でもあるので渡せます。この先の取り違えは止まりません。デバックの節の取り違えが型検査を通ったのも、生の文字列の段階だからです

一方、`user === listName` のように目印の違う値を比べる式は、型検査が止めました。

試しに目印を外して `User` と `ListName` をただの `string` にすると、型検査は失敗し、**実行するテストはすべて通りました。** 区別を確かめているのは型のテストだけです。`vitest` だけを実行していると、区別が消えても気づけません。この版の `npm run check` は、型検査も実行します。

### 項目に、状態と期限を持たせる

変更のストーリーに向けて、項目に状態と期限を足します。状態は 4 つの値だけです。

```typescript
export type ToDoStatus = "Todo" | "InProgress" | "Done" | "Blocked"

export interface ToDoItem {
  readonly description: string
  readonly status: ToDoStatus
  readonly dueDate: Option.Option<DueDate>
}
```

期限は、無いことが普通なので `Option` にします。期限がある場合は、**実在する日付だけ**を表せるようにします。形を正規表現で確かめるだけでは足りません。`Date` を使っても、読めるだけでは足りません。開発機の Node では、次のように読まれました。

| 入力 | `new Date` の結果 |
| :--- | :--- |
| `2026-02-30` | `2026-03-02` になる |
| `2026-02-29` | `2026-03-01` になる |
| `2026-04-31` | `2026-05-01` になる |
| `2026-13-01` | 無効な日付 |

そこで、読んだ日付を文字列に書き戻して、入力と比べます。

```typescript
const isRealDate = (raw: string): boolean => {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(raw)) return false
  const date = new Date(`${raw}T00:00:00Z`)
  // 2 月 30 日のような日付は、読めても別の日になることがあります。書き戻して比べます。
  return !Number.isNaN(date.getTime()) && date.toISOString().startsWith(raw)
}
```

書き戻しの比較を外すと、`2026-02-29`、`2100-02-29`、`2026-02-30`、`2026-04-31` を拒否するテストが失敗しました。

`Date` の値をそのまま項目に持たせないのは、`Date` が後から書き換えられる値だからです。検査した文字列を持ちます。

型の上の指定も、テストで固定します。

```typescript
  // @ts-expect-error 状態は、決めた 4 つの値だけです
  makeToDoItem("x", { status: "Finished" })
  // @ts-expect-error 検査していない文字列は、期限にできません
  makeToDoItem("x", { dueDate: "2026-02-30" })
  // @ts-expect-error 項目は読み取り専用です
  item.status = "Done"
```

3 つ目には限界があります。`Object.assign(item, { status: "Done" })` は、型検査と lint を通りました。`readonly` は書き換えの代入を止めますが、実行時に値を凍らせるものではありません。

状態と期限は、型に足しただけです。画面にはまだ表示せず、どの状態からどの状態に移れるかも決めていません。遷移は第 6 章で扱います。

## まとめ

この章では、次を確かめました。

- ドメインの入口をハブにすると、web が知る domain の部品が 1 つになる
- 依存を引数で渡す形でも、渡し忘れは型検査で止まる。依存が 1 つの比較では、`R` に載せる形との差は、検出ではなく、記述の量と直すファイルの数にあった
- 関数をつないだコードは、矢印を 1 つずつ呼んで調べられる
- `Brand` は、型検査の中で取り違えを止める。実行時の値は区別されない

`npm run check` は、テスト 31 ファイル 160 件が通ります（2026-11-28、Windows、Node 22.23.4）。

次の章では、リストの作成と項目の追加を、起きた出来事の列として表します。

### 型で守れたこと・守れなかったこと

| 対象 | 型検査 | 守ったもの |
| :--- | :--- | :--- |
| ハブや依存の渡し忘れ | **止める** | 型 |
| 利用者とリスト名を、domain の型のまま取り違える | **止める** | 型（`Brand`） |
| 境界で、生の文字列の段階で取り違える | **通る** | HTTP を通るテスト、矢印 1 のテスト |
| 目印を付けた名前を、`string` の引数に取り違えて渡す | **通る** | この章の確認では無し |
| `as` や `JSON.parse` で、検査していない名前を作る | **通る** | この章の確認では無し |
| 検査していない文字列を、そのまま期限にする | **止める** | 型。日付が実在することは、生成の関数とそのテスト |
| 項目の状態を、代入で書き換える | **止める** | 型（`readonly`） |
| 項目の状態を、`Object.assign` で書き換える | **通る** | この章の確認では無し |
| web が取得の依存を読み込む | **通る** | 読み込みの検査 |

### Effectに置き換えたこと・自作で残したこと

| 対象 | この章での扱い |
| :--- | :--- |
| 型の目印と、生成時の検査 | Effect の `Brand` を使う |
| 検査の失敗と、期限が無いこと | Effect の `Result` と `Option` を使う |
| 依存の注入 | 自作する（関数の引数）。`Context.Service` と `Layer` は、比べて採らなかった |
| ドメインの入口（ハブ） | 自作する |
| 日付の実在の確認 | 自作する（`Date` で読み、書き戻して比べる） |

## この章で書いたコード

- `apps/typescript-fp/zettai/zettai-step3-events/src/domain/`（`hub.ts`、`todo-list.ts`、`find-todo-list.ts`）
- `apps/typescript-fp/zettai/zettai-step3-events/src/web/app.ts`
- `apps/typescript-fp/zettai/zettai-step3-events/test/`（`hub.test.ts`、`names.test.ts`、`todo-item.test.ts`、`extract.test.ts`、`boundary.test.ts`）

## 参照

- Uberto Barbini『From Objects to Functions』第 4 章。章の題材と順序、ハブで domain の入口をまとめる考え方は本書に拠っています。本連載のコードはすべて書き起こした自作実装です
- [Effect の公式サイト](https://effect.website/)。この章の API は、版 4.1.6 の型定義と、テストの実行で確かめました
- [TypeScript Handbook の型の互換性](https://www.typescriptlang.org/docs/handbook/type-compatibility.html)
