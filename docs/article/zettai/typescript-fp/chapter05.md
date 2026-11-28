---
type: Article
title: "第 5 章 イベントで状態を変更する"
description: "Zettai 連載 TypeScript FP 版の第 5 章。起きたことをイベントとして記録し、畳み込みで現在の状態を作る。再帰と reduce の限界を測り、状態の変換がモノイドになることを性質テストで確かめる。法則のテストが合成の順序を固定しないことも、壊して示す。"
tags: [article, zettai, typescript, effect, event-sourcing, monoid, chapter]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-28T02:29:19Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-28T02:29:20Z }
---

# 第 5 章 イベントで状態を変更する

第 4 章で、ドメインの入口と型を整えました。この章では、リストを作り、項目を足します。ただし、リストを直接書き換えません。**起きたことを記録し、記録の列から現在の状態を作ります。**

この章のコードは、第 4 章と同じ `zettai-step3-events` にあります。`effect` の版は 4.1.6、性質テストの道具は fast-check 4.11.0 です。第 4 章の `findToDoList` は、この章で引数が変わります。

## ToDoリストの作成の表示

最初の目標は、「作ったリストは、項目の無いリストとして見える」です。

画面からリストを作る操作は、まだありません。そこで受け入れテストでは、**それまでに起きたこと**を準備として渡します。第 3 章の経路は、保管するリストの `Map` を受け取っていました。これを、出来事の列に変えます。

```typescript
// 経路は、それまでに起きた出来事と、driver を使う処理を受け取る高階関数です。
export type Route = <A>(
  history: ReadonlyArray<ToDoListEvent>,
  use: (zettai: ZettaiDriver) => Promise<A>
) => Promise<A>
```

準備は「作って、項目を順に足した」という列で書きます。

```typescript
// 準備は、起きた出来事の列で書きます。
const history = [
  ...createdWith("uberto", "shopping", "milk & <bread>", "eggs"),
  ...createdWith("uberto", "empty"),
  ...createdWith("frank", "gardening", "water the plants")
]
```

新しいシナリオを足します。

```typescript
  it("shows a list that has just been created as a list without items", () =>
    route([...history, ...createdWith("uberto", "new list")], async (zettai) => {
      const uberto = listOwner(zettai, "uberto")

      await uberto.canSee("new list", [])
    }))
```

この時点では、出来事を表すモジュールが無いので、テストの読み込みで失敗します。ここから内側を作ります。利用者の言葉（`listOwner`）は変えていません。**準備の書き方は変わりましたが、期待する振る舞いは第 3 章のままです。**

## 状態変更の保存

### 状態ではなく、起きたことを残す

起きたことを**イベント**と呼びます。名前は過去形です。この章のイベントは 2 つです。

```typescript
// 起きたことを表します。名前は過去形です。_tag で種類を区別します。
export interface ListCreated {
  readonly _tag: "ListCreated"
  readonly user: User
  readonly listName: ListName
}

export interface ItemAdded {
  readonly _tag: "ItemAdded"
  readonly user: User
  readonly listName: ListName
  readonly item: ToDoItem
}

export type ToDoListEvent = ListCreated | ItemAdded
```

`ToDoListEvent` は、2 つの型のどちらかです。`_tag` の値で、どちらかを区別します。網羅の検査は、コマンドが加わる第 6 章で詳しく扱います。

状態を書き換えて保存すると、残るのは最後の結果だけです。イベントを残すと、どうしてその状態になったかが残り、状態はいつでも作り直せます。この章のイベントは、メモリ上の配列です。永続化は第 9 章で扱います。

### 状態は、利用者ごとの入れ子の Map

現在の状態は、利用者とリスト名からリストを引く表です。

```typescript
// 利用者ごとの、入れ子の Map です。利用者と名前の組を、1 つのキーにはしません。
export type ToDoListState = ReadonlyMap<User, ReadonlyMap<ListName, ToDoList>>
```

「利用者と名前の組」を 1 つのキーにしなかったのには、理由があります。開発機の Node で確かめると、次のようになりました。

- 配列の組をキーにすると、同じ中身の別の配列では引けない（`Map` はオブジェクトを参照で比べる）
- `/` でつないだ文字列をキーにすると、`["a/b", "c"]` と `["a", "b/c"]` が同じキーになる

入れ子の `Map` なら、どちらも起きません。区切り文字を含む名前は、テストで固定します。

```typescript
  // "a/b" + "c" と "a" + "b/c" は、/ でつなぐと同じ文字列になります。
  it("does not mix up names that contain a separator", () => {
```

### イベントを 1 件適用する

状態とイベントから、次の状態を作ります。

```typescript
// 元の Map は変えず、変わる道筋の Map だけを作り直します。
const put = (state: ToDoListState, user: User, list: ToDoList): ToDoListState =>
  new Map(state).set(user, new Map(state.get(user)).set(list.listName, list))

// 出来事を 1 件適用して、次の状態を返します。
export const apply = (state: ToDoListState, event: ToDoListEvent): ToDoListState => {
  switch (event._tag) {
    case "ListCreated":
      return put(state, event.user, { listName: event.listName, items: [] })
    case "ItemAdded":
      // 作られていないリストへの追加は、状態を変えません。不正な履歴の拒否は、ここではしません。
      return Option.match(listIn(state, event.user, event.listName), {
        onNone: () => state,
        onSome: (list) => put(state, event.user, { ...list, items: [...list.items, event.item] })
      })
  }
}
```

おかしな履歴の扱いは、次のように決めました。

- **作られていないリストへの追加は、状態を変えません。**
- **同じリストが 2 回作られると、項目の無いリストになります。**

どちらも、誤りとして拒否していません。イベントは「起きたこと」なので、適用する側では断りません。起きてはいけない操作を止めるのは、操作を受け付ける側の仕事です。第 6 章で扱います。

`put` は `Map` を毎回コピーします。利用者やリストが増えると、1 件の適用にかかる処理も増えます。性能は測っていません。

### 前の状態が変わらないこと

`apply` は、受け取った状態を変えません。

```typescript
  it("returns a new state and leaves the previous ones as they were", () => {
    expect(bookIn(emptyState)).toEqual(Option.none())
    expect(bookIn(created)).toEqual(Option.some(listOf("book")))
    expect(bookIn(written)).toEqual(Option.some(listOf("book", "write chapter")))
  })
```

このテストが要るのは、型だけでは守れないからです。`ReadonlyMap` には `set` が無いので、そのまま書き換えようとすると型エラーになります。しかし、`as` で `Map` に言い切ると、型検査と lint を通ります。`put` を、受け取った `Map` に `set` する形に壊すと、テストが 5 件失敗しました。失敗したのは、このテストだけではありません。**共有している `emptyState` が書き換わり、ほかのテストの出発点まで変わりました。**

## 再帰の力を解き放つ

現在の状態は、空の状態から始めて、イベントを順に適用すれば得られます。まず、再帰で書きます。

```typescript
// 先頭の 1 件を適用して、残りを同じ関数に渡します。件数だけ、呼び出しが積み重なります。
export const replayRecursively = (
  events: ReadonlyArray<ToDoListEvent>,
  state: ToDoListState = emptyState,
  index = 0
): ToDoListState => {
  const event = events[index]
  return event === undefined ? state : replayRecursively(events, apply(state, event), index + 1)
}
```

定義がそのまま読めます。「残りが無ければ、いまの状態。あれば、1 件適用して、残りで同じことをする」です。`index` で位置を進めるのは、残りの配列を毎回作らないためです。`noUncheckedIndexedAccess` を有効にしているので、`events[index]` の型には `undefined` が含まれ、終わりの判定を忘れると型エラーになります。

ただし、この再帰には限界があります。呼び出しが件数だけ積み重なるためです。ビルドした出力を、開発機の Node（v22.23.4）で実行して確かめました。

| 形 | 結果 |
| :--- | :--- |
| `replayRecursively` | 6,258 件は成功し、6,259 件で `RangeError` |
| `replay`（次の節の `reduce`） | 20 万件が成功した |

関数の最後で自分を呼ぶ形（末尾呼び出し）にしていますが、この Node では呼び出しが積み重なりました。件数の限界は、環境、関数の形、呼び出し元の深さで変わります。この数字は、この版の実装と 1 台の開発機の結果です。測ったイベントは、同じリストの `ListCreated` を繰り返したものです。

## イベントを畳み込む

同じことは、配列の `reduce` で書けます。

```typescript
// 同じことを、reduce で書きます。呼び出しは積み重なりません。
export const replay = (events: ReadonlyArray<ToDoListEvent>): ToDoListState =>
  events.reduce(apply, emptyState)
```

列を順にたどり、途中の結果を持ち回って 1 つの値にすることを、**畳み込み**と呼びます。`apply` の型が「状態とイベントから、状態」なので、そのまま `reduce` に渡せます。

2 つの形が同じ状態を返すことは、**性質テスト**で確かめます。決まった例の代わりに、道具が生成した入力で、いつも成り立つはずの性質を何度も確かめるテストです。このテストの試行回数は 500 回です。

```typescript
  it("gives the same state by recursion and by reduce", () => {
    fc.assert(
      fc.property(events, (history) => {
        expect(replayRecursively(history)).toEqual(replay(history))
      }),
      { numRuns: TRIALS }
    )
  })
```

再帰の側で最後の 1 件を落とすように壊すと、このテストは失敗しました。

本体は `replay` を使います。ハブが受け取る取得の依存は、状態を引くだけになりました。

```typescript
export const findToDoList =
  (state: ToDoListState): ToDoListFetcher =>
  (user, listName) =>
    listIn(state, user, listName)
```

起動の入口は、見本の履歴を畳み込んで渡します。

```typescript
  const server = yield* serve(makeApp(makeHub(findToDoList(replay(sampleHistory)))), port)
```

web の層は、変えていません。受け入れシナリオは、domain と HTTP の 2 経路で通ります。第 4 章でハブを置いたので、状態の作り方を変えても、web が知る型は同じです。

起動の入口が畳み込むのは、起動のときの 1 回だけです。実行中に状態を変える操作は、まだありません。

## モノイドの発見

### イベントを、状態の変換として見る

`apply` の引数を、片方ずつ与える形に見直します。イベントを決めると、残るのは「状態から状態への関数」です。これを**状態の変換**と呼びます。

```typescript
// 状態から状態への変換です。
export type StateTransition = (state: ToDoListState) => ToDoListState

// 何もしない変換です。合成の単位元です。
export const identityTransition: StateTransition = (state) => state

// first の後に next を適用する、1 つの変換を作ります。
export const andThen =
  (first: StateTransition, next: StateTransition): StateTransition =>
  (state) =>
    next(first(state))
```

2 つの変換をつなぐと、また変換になります。列全体も、1 つの変換にまとめられます。

```typescript
// 出来事の列を、1 つの変換に畳み込みます。
export const transitionOfAll = (events: ReadonlyArray<ToDoListEvent>): StateTransition =>
  events.map(transitionOf).reduce(andThen, identityTransition)
```

ここには、次の 3 つがそろっています。

1. 同じ種類の 2 つを組み合わせて、同じ種類の 1 つを作る操作がある（`andThen`）
2. **結合律:** 3 つを組み合わせるとき、どこから先に組み合わせても結果が同じ
3. **単位元:** 組み合わせても相手を変えない値がある（`identityTransition`）

この組を**モノイド**と呼びます。整数と足し算と 0、配列と連結と空配列も、同じ形です。

### 畳み込みと、モノイドは違う

`replay` も `transitionOfAll` も `reduce` を使いますが、組み合わせているものが違います。

| 形 | 組み合わせるもの | モノイドの操作か |
| :--- | :--- | :--- |
| `replay`: `events.reduce(apply, emptyState)` | **状態**と**イベント**から状態 | 違う。2 つの引数の型が違う |
| `transitionOfAll`: `reduce(andThen, identityTransition)` | **変換**と**変換**から変換 | モノイドの操作 |

状態の畳み込みは、モノイドでなくても書けます。モノイドになるのは、変換の合成です。結合律があるので、変換は、列を好きな位置で分けて先にまとめても、同じ結果になります。**順序を入れ替えてよい、という意味ではありません。** 作成の前に追加を適用すると、追加は無視されます。

```typescript
  it("is not commutative", () => {
    const create = transitionOf(listCreated(userOf("uberto"), listNameOf("book")))
    const add = transitionOf(itemAdded(userOf("uberto"), listNameOf("book"), makeToDoItem("x")))

    expect(andThen(create, add)(emptyState)).not.toEqual(andThen(add, create)(emptyState))
  })
```

### 法則を性質テストにする

関数どうしは、`===` や `toEqual` で比べても、振る舞いが同じかは分かりません。そこで、**生成した状態に適用して、結果の状態を比べます。**

```typescript
// 関数どうしは比べられません。生成した状態に適用して、結果を比べます。
// 生成するのは、変換ではなく出来事の列です。反例に、原因の出来事が表示されます。
const sameOn = (left: StateTransition, right: StateTransition, history: History) => {
  const state = replay(history)
  expect(left(state)).toEqual(right(state))
}
```

```typescript
  it("has identityTransition as the identity on both sides", () => {
    fc.assert(
      fc.property(events, events, (history, before) => {
        const a = transitionOfAll(history)
        sameOn(andThen(identityTransition, a), a, before)
        sameOn(andThen(a, identityTransition), a, before)
      }),
      { numRuns: TRIALS }
    )
  })
```

最初は、生成した**変換**をそのまま引数にしていました。失敗したときの反例が `(state) => next(first(state))` と表示され、どんな入力で失敗したかが読めませんでした。**イベントの列を生成して、テストの中で変換にすると**、反例が読めるようになります。`identityTransition` が空の状態を返すように壊すと、次のように表示されました。

```bash
Counterexample: [[{"_tag":"ListCreated","user":"uberto","listName":"book"}],[]]
Shrunk 6 time(s)
```

「リストを 1 つ作る変換」と「何も起きていない状態」で失敗する、と読めます。fast-check は、失敗した入力を小さくしてから表示します。

このテストが確かめるのは、**生成した入力の範囲**です。法則の証明ではありません。

### 何も起きない入力ばかりでは、意味がない

`ItemAdded` は、作られていないリストには効きません。生成するリスト名の種類が多いと、追加はほとんど効かず、法則のテストは「何もしない変換どうし」を比べて通ってしまいます。そこで、名前の候補を少なくします。

```typescript
// 名前の候補を少なくします。多いと、作ったリストに項目が足されることが、ほとんど起きません。
const user = fc.constantFrom("uberto", "frank").map(userOf)
const listName = fc.constantFrom("book", "a/b").map(listNameOf)
```

少なくしたつもりで終わらせず、**追加が実際に効く列がどれだけあるかをテストにします。**

```typescript
    const samples = fc.sample(events, { numRuns: TRIALS, seed: 1 })

    const effective = samples.filter(addsToAnExistingList).length

    expect(effective).toBeGreaterThan(TRIALS / 5)
```

3 つのシードで 500 列ずつ数えると、追加が 1 回以上効いた列は 192〜220 列でした。リスト名を 8 文字以上のでたらめな文字列に変えると、このテストが失敗しました。

### 法則は、順序を固定しない

合成をわざと壊して、どのテストが失敗するかを確かめました。

| 壊し方 | 失敗したテスト |
| :--- | :--- |
| 単位元が、空の状態を返す | 単位元 |
| `andThen` が、最初の変換を忘れる | 単位元、起きた順の合成 |
| `andThen` が、利用者が 2 人以上増える結果を捨てる | 結合律、起きた順の合成 |
| `andThen` が、**逆の順に適用する** | **起きた順の合成だけ** |

どの壊し方も、型検査は通ります。

最後の行が大事です。**逆の順に適用する合成も、結合律と単位元を満たします。** それも別のモノイドだからです。法則のテストだけでは、「起きた順に適用する」ことは固定できません。そこで、畳み込みの結果と比べるテストを足します。

```typescript
  // 法則は、合成の順序を逆にしても成り立ちます。順序は、畳み込みと比べて固定します。
  it("composes the events in the order they happened", () => {
    fc.assert(
      fc.property(events, (history) => {
        expect(transitionOfAll(history)(emptyState)).toEqual(replay(history))
      }),
      { numRuns: TRIALS }
    )
  })
```

### 合成した変換にも、限界がある

`transitionOfAll` は `reduce` で書いていますが、作られるのは、**関数が関数を呼ぶ長い列**です。適用すると、呼び出しが積み重なります。再帰の節と同じ条件で測ると、9,375 件は成功し、9,376 件で `RangeError` になりました。

モノイドの法則は「どこで区切ってもよい」ことを約束しますが、実行時の呼び出しの深さは約束しません。この版の本体は、状態を順に作る `replay` を使います。

### Effect の Reducer と比べる

Effect 4.1.6 には、`Monoid` という名前のモジュールは見当たりません。近いのは `Reducer` で、結合する操作と初期値を持ちます。

<!-- code-check: ignore 比較に使った一時的なコード -->
```typescript
const transitions = Reducer.make<StateTransition>(andThen, identityTransition)

transitions.combineAll(history.map(transitionOf))(emptyState)
```

この形は、生成した 500 列で `replay` と同じ状態を返しました。`Reducer.flip` は、順序を逆にした `Reducer` を返します。ただし、**`Reducer.make` は、結合律を満たさない引き算も受け取りました。** `Reducer` の型は、法則を保証しません。置き換えても、法則のテストは同じだけ必要です。

いまは、モノイドを値として受け取る汎用の処理がないので、**この版は自作のままにします**（[ADR-059](../../../adr/ADR-059-typescript-own-state-transition-monoid.md)）。

## まとめ

この章では、次を確かめました。

- 状態は、イベントの列を畳み込んで作れる。`apply` は前の状態を変えない
- 再帰は定義が読みやすいが、件数に限界がある。この版の本体は `reduce` を使う
- 状態の変換は、合成と何もしない変換でモノイドになる。状態の畳み込みそのものとは別のもの
- 法則のテストは、順序を固定しない。順序は別のテストで固定する
- 性質テストは、生成した入力が意味のある場合を含むかを、あわせて確かめる

`npm run check` は、テスト 34 ファイル 181 件が通ります（2026-11-28、Windows、Node 22.23.4）。

イベントは、いまは準備として渡しているだけです。利用者の操作からイベントを作る部分は、まだありません。次の章では、利用者の意図をコマンドとして受け取り、状態を見て、起こしてよいイベントを決めます。

### 型で守れたこと・守れなかったこと

| 対象 | 型検査 | 守ったもの |
| :--- | :--- | :--- |
| `ReadonlyMap` の状態を、そのまま `set` する | **止める** | 型 |
| `as` で `Map` に言い切って書き換える | **通る** | 前の状態が変わらないことのテスト |
| 再帰で、最後のイベントを落とす | **通る** | 再帰と `reduce` を比べる性質テスト |
| 合成の順序を逆にする | **通る** | 畳み込みと比べる性質テスト（法則のテストは通る） |
| 結合律や単位元を壊す | **通る** | 法則の性質テスト |
| 件数が多くて、呼び出しが積み重なる | **通る** | 本体が `reduce` で畳み込むこと（テストでは固定していない） |
| 生成する入力が、意味のある場合を含まない | **通る** | 追加が効く割合のテスト |

### Effectに置き換えたこと・自作で残したこと

| 対象 | この章での扱い |
| :--- | :--- |
| 「無いかもしれない」リストの扱い | Effect の `Option` を使う |
| イベントと状態 | 自作する（`_tag` を持つ型の和と、入れ子の `Map`） |
| 畳み込み | 配列の `reduce` を使う |
| 状態の変換のモノイド | 自作する。`Reducer` は、比べて採らなかった |
| 性質テストの道具 | fast-check を使う（[ADR-055](../../../adr/ADR-055-typescript-testing-tools.md)） |

## この章で書いたコード

- `apps/typescript-fp/zettai/zettai-step3-events/src/domain/`（`events.ts`、`state.ts`、`replay.ts`、`transition.ts`、`find-todo-list.ts`）
- `apps/typescript-fp/zettai/zettai-step3-events/src/application/sample-history.ts`
- `apps/typescript-fp/zettai/zettai-step3-events/test/`（`state.test.ts`、`replay.test.ts`、`transition.test.ts`、`support/arbitraries.ts`、`support/history.ts`、`acceptance/`）

## 参照

- Uberto Barbini『From Objects to Functions』第 5 章。章の題材と順序、イベントの畳み込みからモノイドを見出す考え方は本書に拠っています。本連載のコードはすべて書き起こした自作実装です
- [fast-check の公式ドキュメント](https://fast-check.dev/)
- [Effect の公式サイト](https://effect.website/)。`Reducer` は、版 4.1.6 の型定義、配布された JavaScript、テストの実行で確かめました
