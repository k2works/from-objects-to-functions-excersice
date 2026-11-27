---
type: Article
title: "第 3 章 ドメインの定義とテスト"
description: "Zettai 連載 TypeScript FP 版の第 3 章。受け入れテストを業務の言葉で書き直し、driver を渡す高階関数で経路を差し替えて、同じシナリオを domain と HTTP の両方で実行する。HTTP の読み戻しが壊れた画面を見逃さないことと、await を忘れると vitest が全件成功と表示することを確かめる。"
tags: [article, zettai, typescript, effect, testing, chapter]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-27T21:00:23Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-27T21:00:39Z }
---

# 第 3 章 ドメインの定義とテスト

第 2 章で、HTTP の縦串が通りました。この章では、その上の受け入れテストを作り直します。**同じシナリオを、domain を直接呼ぶ経路と、HTTP を通る経路の両方で実行します。**

## 受け入れテストを改善する

第 2 章の外側のテストは、次のような検査をしていました。

```typescript
  expect(response.status).toBe(200)
  expect(response.contentType).toBe("text/html; charset=utf-8")
  expect(response.body).toContain("<li>write chapter</li>")
```

縦串がつながったことを確かめるには足ります。しかし、このテストは**何ができるか**ではなく、**HTTP と HTML がどう見えるか**を書いています。画面の作りを変えると、利用者にできることが変わらなくても、テストを直すことになります。また、HTTP を通すので、業務の規則が正しいかと、画面が正しいかを分けられません。

受け入れテストは、利用者の言葉で書きます。

- 利用者は、自分のリストの項目を、順番どおりに見られる
- 利用者は、空のリストを、項目の無いリストとして見られる
- 利用者は、ほかの利用者のリストを見られない
- 利用者は、存在しないリストを見られない

## 高階関数を使う

シナリオから HTTP を消すには、「項目を見る」という操作と、それをどう実現するかを分けます。操作の型を **driver** と呼びます。

```typescript
// 業務の言葉の操作です。HTTP も HTML も現れません。
export interface ZettaiDriver {
  readonly itemsOf: (
    user: User,
    listName: ListName
  ) => Promise<Option.Option<ReadonlyArray<string>>>
}

// 経路は、保管するリストと、driver を使う処理を受け取る高階関数です。
export type Route = <A>(
  store: ToDoListStore,
  use: (zettai: ZettaiDriver) => Promise<A>
) => Promise<A>
```

`Route` は、**関数を引数に受け取る関数**、高階関数です。経路は、準備をしてから `use` を呼び、`use` が終わったら後始末をします。シナリオは、`use` に渡す関数として書きます。準備と後始末を呼び出し側に書かせないので、シナリオは業務の操作だけになります。

型引数 `<A>` は、`use` の結果をそのまま経路の結果にすることを表します。経路は、結果の中身を知る必要がありません。

比べた形は、ほかに 2 つあります。driver を `Context.Service` にして `Layer` で差し替える形と、`Effect` を返す driver を `Scope` つきで作る形です。**3 つの形で、見つけられる誤りに差はありませんでした。** `Layer` は、依存の注入を扱う第 4 章に残し、この章は高階関数にしました（[ADR-057](../../../adr/ADR-057-typescript-acceptance-route-as-higher-order-function.md)）。

### 利用者の言葉を作る

driver の上に、利用者の言葉を 1 段重ねます。

```typescript
// 利用者の言葉で、できることと、できないことを書きます。
export const listOwner = (zettai: ZettaiDriver, userName: string) => {
  const itemsOf = (listName: string) => zettai.itemsOf({ name: userName }, { name: listName })

  return {
    canSee: async (listName: string, items: ReadonlyArray<string>) => {
      expect(await itemsOf(listName)).toEqual(Option.some(items))
    },
    cannotSee: async (listName: string) => {
      expect(await itemsOf(listName)).toEqual(Option.none())
    }
  }
}
```

`listOwner` も、driver を受け取って操作の組を返す関数です。シナリオは次のようになります。

```typescript
  it("does not show the lists of another user", () =>
    route(store, async (zettai) => {
      const uberto = listOwner(zettai, "uberto")
      const frank = listOwner(zettai, "frank")

      await frank.canSee("gardening", ["water the plants"])
      await uberto.cannotSee("gardening")
      await frank.cannotSee("shopping")
    }))
```

`describe.each` で、同じシナリオを経路ごとに実行します。

```typescript
describe.each(Object.entries(routes))("viewing ToDo lists via %s", (_name, route) => {
```

最初は、どちらの経路も「まだ無い」と失敗する仮の実装にします。4 つのシナリオ × 2 経路の 8 件が、すべて失敗します。

```bash
Error: この経路は、まだありません
      Tests  8 failed (8)
```

### await を忘れると、全件成功と表示される

シナリオの操作は `Promise` を返します。`await` を忘れたら、何が起きるでしょうか。期待する項目も、わざと誤らせて試しました。

<!-- code-check: ignore わざと壊したコード -->
```typescript
uberto.canSee("shopping", ["wrong"])
```

**vitest は、`Tests  14 passed (14)` と表示しました。** テストの関数は、検査が終わる前に終了したためです。後から失敗した検査は「未処理のエラー」として別に表示され、コマンドの終了コードは 1 になりました。件数の行だけを見ると、見落とします。

型検査は、このコードを通しました。**lint の `no-floating-promises` は、書いた行を指して失敗しました。**

```bash
viewing-lists.test.ts:21:7: error typescript(no-floating-promises): Promises must be awaited
```

利用者の言葉を作ると、シナリオは読みやすくなります。一方で、呼び出しの形から非同期であることが見えにくくなります。この版では、品質ゲートの lint が、その見落としを止めます。

## ドメインとインフラストラクチャの分離

最初に作る経路は、domain を直接呼ぶ経路です。

```typescript
// domain を直接呼ぶ経路です。サーバーも HTML もありません。
export const domainRoute: Route = (store, use) => {
  const fetchList = findToDoList(store)
  return use({
    itemsOf: async (user, listName) =>
      fetchList(user, listName).pipe(
        Option.map((list) => list.items.map((item) => item.description))
      )
  })
}
```

この経路が成り立つのは、第 2 章で domain が HTTP を知らない形にしたからです。`findToDoList` は、`Request` も HTML も受け取りません。

この分離は、2 つのテストで守ります。1 つは第 2 章の、domain が読み込む相手の検査です。もう 1 つは、この章で足した、**domain の経路が web を読み込まないこと**の検査です。domain の経路が HTML の関数を通るようになると、2 つの経路が同じ部分を通ります。経路を分けた意味が薄れるためです。

```typescript
// domain の経路が web を通ると、2 つの経路が同じ部分を通り、分けた意味が薄れます。
describe("domain route", () => {
  it("does not go through the web", () => {
```

domain の経路に HTML の関数の読み込みを足すと、型検査は通り、このテストが失敗しました。

## ドメインからテストを駆動する

domain の経路を入れた時点で、**4 つのシナリオが domain だけで通り、HTTP の 4 件が失敗する**状態になります。

```bash
 FAIL  … > viewing ToDo lists via http > shows the owner the items of a list in order
      Tests  4 failed | 4 passed (8)
```

ここに順序があります。まず、シナリオで業務の規則を決め、domain の経路で確かめます。サーバーも画面も要らないので、失敗の原因は、業務の規則か、シナリオにあります。次に HTTP の経路を足します。**ここで初めて失敗したら、原因は HTTP と画面の側に絞れます。**

この章では、新しい業務の機能は足していません。4 つのシナリオが求める振る舞いは、第 2 章の `findToDoList` がすでに満たしていました。この章で Red から Green にしたのは、テストの経路です。シナリオから新しい機能を作る流れは、第 4 章以降で扱います。

## DDT を driver のインターフェースに変換する

原著は、この節で受け入れテストを Kotlin のライブラリ Pesticide の形にします。Pesticide は TypeScript にはありません。この版では、**同じ型の driver を経路ごとに作る**ことで、同じ目的を果たします。仕様を経路から独立させる、という目的です。受け入れテストの道具にライブラリを入れない判断は、[ADR-055](../../../adr/ADR-055-typescript-testing-tools.md) にあります。

### HTTP の driver は、画面を読み戻す

HTTP の driver は、要求を送り、返った HTML から値を読み戻します。**読み戻し方が甘いと、壊れた画面でもシナリオが通ります。** そこで、次の場合は失敗にします。

```typescript
    if (response.status === 404) return Option.none()
    // 200 と 404 のほかは失敗にします。500 を「リストが無い」と読むと、壊れていても通ります。
    if (response.status !== 200) throw new Error(`unexpected status ${response.status}`)
    const page = readListPage(await response.text())
    if (page.name !== listName.name) throw new Error(`unexpected page of ${page.name}`)
    return Option.some(page.items)
```

- **200 と 404 のほかの状態:** 500 を「リストが無い」と読むと、「ほかの利用者のリストは見られない」が、サーバーが壊れていても通ります
- **別のリストの画面:** 見出しを確かめないと、別の画面が 200 で返っても、項目が 0 件と読めて「空のリスト」が通ります
- **エスケープされていない値:** HTML の中身をそのまま取り出すと、エスケープを忘れた画面でも、期待どおりの文字列が読めてしまいます

読み戻しは、テストのコードですが、判定を持ちます。そのため、読み戻し自体にもテストを書きました。最初の素朴な読み戻しでは、4 件すべてが失敗しました。

```typescript
const NOT_ESCAPED = /[<>"']|&(?!(?:amp|lt|gt|quot|#39);)/

// エスケープされていない値を見つけたら、読み戻しを失敗にします。
const readText = (raw: string): string => {
  if (NOT_ESCAPED.test(raw)) throw new Error(`not escaped: ${raw}`)
  return raw.replace(/&(?:amp|lt|gt|quot|#39);/g, (entity) => entities[entity] ?? entity)
}
```

エスケープの確認が効くのは、**シナリオのデータに、エスケープが必要な文字があるとき**です。`milk & <bread>` という項目を入れているのは、そのためです。

この読み戻しは、HTML を解析していません。`<h1>` と `<li>` を正規表現で探すだけです。画面の作りを変えると、ここを直すことになります。直すのは 1 か所で、シナリオは変わりません。

### 経路の後始末を確かめる

HTTP の経路は、サーバーを起動し、`use` が終わると停止します。

```typescript
// 実サーバーを起動する経路です。処理が終わると、成功でも失敗でも停止します。
export const httpRoute: Route = (store, use) =>
  Effect.runPromise(
    Effect.scoped(
      Effect.gen(function* () {
        const server = yield* serve(makeApp(findToDoList(store)), 0)
        return yield* Effect.promise(() => use(driverFor(server.port)))
      })
    )
  )
```

後始末のうち、型が守る範囲を確かめました。`Effect.scoped` を外すと、**型検査が止めます。** `serve` の `Effect` は、要求の型 `R` に `Scope` を持つためです。

```bash
error TS377004: This Effect requires a service that is missing from the expected Effect context: `Scope`. effect(missingEffectContext)
```

一方、**閉じないスコープを渡すと、型検査も lint も通ります。**

<!-- code-check: ignore わざと壊したコード -->
```typescript
const scope = yield* Scope.make()
const server = yield* serve(makeApp(findToDoList(store)), 0).pipe(Scope.provide(scope))
```

型が求めるのは「スコープがあること」で、「そのスコープが閉じられること」ではありません。さらに、経路の型 `Route` は `Promise<A>` を返すだけで、後始末の約束を表しません。そこで、経路の外に持ち出した driver が、経路の終了後に使えないことをテストで確かめます。シナリオが失敗した場合も、同じことを確かめます。上の壊れた経路では、この 2 件が失敗しました。

```typescript
  it("stops the server after the scenario", async () => {
    const leaked = await httpRoute(new Map(), async (zettai) => zettai)

    await expect(ask(leaked)).rejects.toThrow("fetch failed")
  })
```

経路の型が後始末の約束を表さないことが、高階関数の形を選んだ代償の 1 つです。もう 1 つは、経路の境目で `Effect` から `Promise` に変わることです。サーバーの起動の失敗は、型付きの `ServerError` ではなく、`Promise` の失敗になります。

### 経路が違うと、見つかる誤りが違う

本物の実装を、わざと壊して受け入れテストを実行しました。

| 壊し方 | 失敗した経路 |
| :--- | :--- |
| 利用者を確かめずにリストを返す | **domain と HTTP** |
| HTML が最後の項目を落とす | HTTP だけ |
| HTML のエスケープを忘れる | HTTP だけ（`not escaped`） |
| 無いリストで 500 を返す | HTTP だけ（`unexpected status 500`） |
| 別のリストの画面を返す | HTTP だけ（`unexpected page of another`） |

**両方の経路が失敗すれば、業務の規則か、domain を疑います。HTTP の経路だけが失敗すれば、画面と接続を疑います。** 経路を分けると、失敗の場所の見当がつきます。ただし、これは今回試した壊し方での結果です。HTTP の経路だけの失敗が、必ず画面や接続の誤りとは限りません。

## まとめ

この章では、次を確かめました。

- 受け入れシナリオは、業務の言葉の driver の上に書くと、HTTP と HTML を含まなくなる
- 経路は、準備と後始末を持つ高階関数として表せる
- 画面を読み戻す側が甘いと、壊れた画面でもシナリオが通る。読み戻しにもテストが要る
- `await` を忘れると、vitest の件数の表示は全件成功になる。この版では lint が止める

`npm run check` は、テスト 17 ファイル 67 件が通ります（2026-11-27、Windows、Node 22.23.4）。`nixos/nix` のコンテナ（Linux、Node 24.12.0）では、型検査、lint、format、smoke と、PostgreSQL のテストを除く 66 件が通りました。**この章のコードは、CI ではまだ確かめていません。** 状態を変える操作を含むシナリオは、まだありません。

これで、HTTP の縦串と、2 経路の受け入れテストがそろいました。次の章では、依存の渡し方を比べ、`User` と `ListName` を型で区別します。

### 型で守れたこと・守れなかったこと

| 対象 | 型検査 | 守ったもの |
| :--- | :--- | :--- |
| 経路の driver で、操作の名前を書き間違える | **止める** | 型 |
| シナリオの操作の `await` を忘れる | **通る** | lint（vitest は件数の上では全件成功。終了コードは失敗） |
| domain の経路が web を読み込む | **通る** | 読み込みの検査 |
| 経路で `Effect.scoped` を忘れる | **止める**（`R` に `Scope` が残る） | 型 |
| 経路で、閉じないスコープを渡す | **通る** | 経路のテスト |
| 画面のエスケープの欠落、別の画面 | **通る** | HTTP の経路の読み戻し |

型の名前の取り違えは、第 2 章と同じく型では止まりません。`itemsOf(user, listName)` の引数を入れ替えても、型の形は同じです。

### Effectに置き換えたこと・自作で残したこと

| 対象 | この章での扱い |
| :--- | :--- |
| 受け入れテストの経路と driver | 自作する。`Promise` を返す高階関数にし、`Layer` は使わない |
| 利用者の言葉（`listOwner`） | 自作する |
| 受け入れテストの道具 | ライブラリを入れない。vitest の `describe.each` を使う |
| HTTP の経路の後始末 | 経路の中で、Effect の `Scope`（`Effect.scoped`）を使う |
| 画面の読み戻し | 自作する |

## この章で書いたコード

- `apps/typescript-fp/zettai/zettai-step2-http-domain/test/acceptance/`（`driver.ts`、`actor.ts`、`domain-route.ts`、`http-route.ts`、`routes.ts`、`page.ts`、`viewing-lists.test.ts`、`page.test.ts`、`routes.test.ts`）
- `apps/typescript-fp/zettai/zettai-step2-http-domain/test/boundary.test.ts`

この章で、`src/` のコードは変えていません。

## 参照

- Uberto Barbini『From Objects to Functions』第 3 章。章の題材と順序、利用者の言葉で受け入れテストを書く考え方は本書に拠っています。本連載のコードはすべて書き起こした自作実装です
- [vitest の `describe.each`](https://vitest.dev/api/describe)
- [typescript-eslint の `no-floating-promises`](https://typescript-eslint.io/rules/no-floating-promises/)（この版は、同じ名前の Oxlint のルールを使います）
