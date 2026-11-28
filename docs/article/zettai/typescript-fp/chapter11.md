---
type: Article
title: "第 11 章 アプリカティブによるデータバリデーション"
description: "Zettai 連載 TypeScript FP 版の第 11 章。画面からリストの名前を変え、入力の問題をまとめて返す。自作の合成で問題を蓄積し、保存済みの値を読めなくする規則の掛け方、整形で出力が変わるテンプレート、CSRF の対策が無いことを確かめる。"
tags: [article, zettai, typescript, effect, validation, applicative, chapter]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-28T05:48:17Z }
---

# 第 11 章 アプリカティブによるデータバリデーション

第 10 章で、コマンドを安全に処理できるようになりました。ただし、画面からは、まだ何も変えられません。この章では、**画面からリストの名前を変えます。** 利用者の入力は誤ることがあります。**誤りは、1 つずつではなく、まとめて返します。**

この章のコードは `zettai-step9-validation` にあります。第 10 章の step を複製して始めました。版は、`effect` 4.1.6、`pg` 8.23.1 です。ライブラリは追加していません。

## ToDoリストの名前変更

名前の変更は、2 つのリストに触れます。古い名前のストリームと、新しい名前のストリームです。

- 古いストリームに、`ListRenamed` を追記する
- 新しいストリームに、`ListCreated` と、いまの項目の `ItemAdded` を追記する

```typescript
const rename = ({ user, listName, newName }: RenameToDoList, state: ToDoListState): Decision =>
  existingList(state, user, listName).pipe(
    Result.flatMap((list): Decision =>
      Option.isSome(listIn(state, user, newName))
        ? Result.fail({ _tag: "ListAlreadyExists", user, listName: newName })
        : Result.succeed([
            listRenamed(user, listName, newName),
            listCreated(user, newName),
            ...list.items.map((item) => itemAdded(user, newName, item))
          ])
    )
  )
```

新しい名前は空いていなければなりません。同じ名前への変更も、「すでにある」で断ります。項目は、状態も含めて、いまの値で書き直します。**状態を変えた履歴は、新しいストリームに引き継ぎません。** 名前を変えて元に戻しても、項目が二重にならないことは、テストで固定します。一覧では、名前を変えたリストは末尾に移ります。

イベントの種類が増えると、型検査が直す場所を教えます。`ListRenamed` を足した後でさらに 1 種類足すと、行の読み方の表（`TS2741`）と、行の書き方、状態の適用、射影（`TS2366`）で失敗しました。

コマンドが読み書きするストリームは、コマンドから決めます。

```typescript
// コマンドが読み書きするストリームです。名前の変更は、古い名前と新しい名前の 2 つです。
export const streamsFor = (command: ToDoListCommand): ReadonlyArray<StreamId> =>
```

第 10 章の仕組みが、ここで効きます。2 つのストリームを、決まった順にロックして読みます。ストリームを古い名前だけにするように壊すと、**新しい名前への追記が「対象でないストリーム」として拒まれ**、テストが失敗しました。

2 つの別々のリストを、同時に同じ新しい名前に変える組を、PostgreSQL で 20 回実行しました。20 回とも、片方が成功し、もう片方は `ListAlreadyExists` になりました。両方が新しい名前のストリームをロックするからです。

## ２つのパラメータを持った検証

新しい名前には、3 つの条件があります。空でないこと、40 文字以内であること、保存できる文字であることです。利用者名にも条件があります。名前の変更では、**2 つの入力を、独立に確かめます。** 片方に誤りがあっても、もう片方の確認は実行します。

```typescript
// 名前の変更の 2 つの入力を、独立に確かめます。
export const validateRename = (
  rawUser: string,
  rawNewName: string
): Validation<{ readonly user: User; readonly newName: ListName }, InputProblem> =>
```

利用者名が空で、新しい名前が長すぎると、2 件の問題が返ります。

いまの画面では、利用者名はパスから来て、境界の読み取りを通った値です。そのため、実際のフォームで返るのは新しい名前の問題だけです。2 つの入力の問題がそろう場合は、domain のテストで確かめます。

### 制限を掛ける場所

40 文字の制限は、**これから入力する名前**の規則です。第 4 章の名前の生成に、そのまま足すと、保存した行を読むときにも掛かります。**過去に保存した長い名前のイベントが、読めなくなります。** 第 9 章で、読めない行は defect にすると決めたので、そのリストは 500 になります。

そこで、2 つに分けます。

```typescript
// 保存できる名前かを確かめます。保存した行を読むときも、同じ条件を使います。
const storedName = <A>(raw: string, name: (raw: string) => A): Validation<A, NameProblem> =>
  zipWith(notBlank(raw), storable(raw), () => name(raw))
```

```typescript
// 利用者が新しく入力する名前には、長さの制限も掛けます。
export const validateNewListName = (raw: string): Validation<ListName, NameProblem> =>
```

**この分け方は、最初のテストでは守られていませんでした。** 保存した行を新しい名前の規則で読むように壊しても、**テストはすべて通りました。** 長い名前を保存して読み戻すテストが無かったためです。保管の契約に足すと、壊した実装で失敗しました。

```typescript
  // 新しい名前の長さの制限を、保存済みの行の読み取りに掛けると、過去の出来事が読めなくなります。
  it("reads a saved list whose name is longer than the limit for new names", async () => {
```

検証の規則を厳しくするときは、**保存済みの値を読めるか**を確かめます。

## バリデーションを使った検証

第 7 章の `Result.flatMap` は、最初の失敗で止まります。後ろの処理が、前の結果を使うからです。独立した検証には、前の結果が要りません。Effect の `Result.all` も、2 つの失敗を渡すと、最初の 1 つだけを返しました。

Effect 4.1.6 には、問題を集める仕組みもあります。`Effect.validate` は配列の要素の失敗を集め、`Schema` は `errors: "all"` で項目の問題を集めます。この版の検証は実行を伴わず、核は `Effect` の型を読み込みません。問題は業務の型にしたいので、**`Result` の失敗を配列にして、合わせる関数を自作しました**（[ADR-065](../../../adr/ADR-065-typescript-validation-and-rename-form.md)）。

```typescript
// 検証の結果です。失敗は、見つけた問題を順に並べた配列にします。
export type Validation<A, E> = Result.Result<A, ReadonlyArray<E>>
```

```typescript
// 2 つの検証を、どちらも実行して合わせます。片方でも失敗なら、両方の問題を左から順に並べます。
export const zipWith = <A, B, C, E>(
  left: Validation<A, E>,
  right: Validation<B, E>,
  combine: (a: A, b: B) => C
): Validation<C, E> =>
  Result.isSuccess(left) && Result.isSuccess(right)
    ? Result.succeed(combine(left.success, right.success))
    : Result.fail([...problemsOf(left), ...problemsOf(right)])
```

型を見ると、違いが分かります。`flatMap` の次の処理は、前の値を受け取る関数です。`zipWith` の `right` は、関数ではなく、**もう出来上がった結果**です。前の値に依存できないので、両方を実行できます。

## アプリカティブファンクタの結合

独立した 2 つの結果を、関数で合わせる操作を持つファンクタを、**アプリカティブファンクタ**と呼びます。3 つ以上は、`zipWith` を重ねます。重ね方で結果が変わらないことが大事です。性質テストで確かめます。試行回数は 500 回です。

```typescript
  it("does not depend on how three validations are grouped", () => {
    fc.assert(
      fc.property(validation, validation, validation, (a, b, c) => {
        const leftFirst = zipWith(zipWith(a, b, pair), c, ([x, y], z) => [x, y, z])
        const rightFirst = zipWith(a, zipWith(b, c, pair), (x, [y, z]) => [x, y, z])
```

ほかに、成功どうしは両方の値を保つこと、失敗の問題は左から順に並ぶことを確かめます。**順序は、利用者に見える仕様**です。逆順につなぐように壊すと、受け入れシナリオを含むテストが失敗しました。最初の失敗で止まるように壊しても、同じように失敗しました。どちらも型検査は通ります。

アプリカティブの法則は、これで全部ではありません。この版が確かめたのは、結合、成功の値の保存、問題の順序です。

## ユーザインタフェースの改善

### テンプレート

フォームを足すと、画面の組み立てが複雑になります。第 2 章の方式は、置く値を 1 つずつエスケープしていました。忘れると、そのまま出力されます。

そこで、**置く文字列を必ずエスケープするタグ付きテンプレート**にします。

```typescript
type Value = string | number | Html | ReadonlyArray<Html>

// 文字列は必ずエスケープします。テンプレートが作った断片は、そのまま置きます。
const place = (value: Value): string => {
```

エスケープしないのは、テンプレート自身が作った断片だけです。断片は、`#` で始まる非公開の項目を持つクラスにしています。**同じ形のオブジェクトを渡しても、型検査が拒みます。** 値が無いかもしれないもの（`string | undefined`）も拒みます。テンプレートの穴は TypeScript の式なので、渡し忘れは起きません。

```typescript
  // @ts-expect-error 値が無いかもしれないものは、置けません
  markup`<p>${missing}</p>`,
  // @ts-expect-error 同じ形のオブジェクトは、検査済みの HTML の代わりになりません
  markup`<p>${{ text: "<script>" }}</p>`
```

タグの名前は、最初は `html` でした。すると、**Prettier が中身を HTML として整形し、改行と空白を足しました。** 整形すると、画面の出力が変わります。出力を比べるテストが失敗して気づきました。

```typescript
// タグの名前を html にしないでください。Prettier が中身を HTML として整形し、出力に空白が増えます。
```

このテンプレートは、本文と属性の値のエスケープだけをします。URL やスクリプトの中は区別しません。URL は、組み立てる側で `encodeURIComponent` を使います。

問題の文言は、種類ごとの表です。問題の種類を足すと、`TS2741` で止まります。

### 画面からコマンドを送る

| メソッドと URL | 結果 |
| :--- | :--- |
| `GET /todo/{user}/{listname}/rename` | フォーム。リストが無ければ 404 |
| `POST` で成功 | 302。新しい名前の画面へ送る |
| `POST` で入力に問題 | 400。問題をすべて並べた同じフォーム |
| `POST` で業務の失敗 | 理由に応じた状態と、固定の本文 |

成功で画面を直接返さず、別の URL に送るのは、再読み込みで同じ送信が繰り返されないようにするためです。入力に問題がある場合は、入力した値をフォームに残します。値は、テンプレートがエスケープします。

```typescript
    const validated = validateRename(user, input)
    if (Result.isFailure(validated)) {
      yield* hub.getList(user, listName)
      return htmlResponse(renderRenameForm(user, listName, input, validated.failure), 400)
    }
```

`node:http` への接続は、第 2 章から GET だけを扱ってきました。この章で、POST の本文と `content-type` を渡します。本文は 10,000 バイトまでで、超えると 413 です。上限が無いと、大きな本文がメモリに積み上がります。

受け入れシナリオでは、HTTP の 2 つの経路が、**初めて画面からコマンドを送ります。** 名前の変更と、2 つの問題が一度に返ることを、3 経路で確かめます。

### この画面に無い守り

実際のサーバーに、別のサイトを装った `Origin` をつけて POST しました。**名前は変わりました。** この版には、CSRF の対策も、認証もありません。開いている別のサイトが、利用者のブラウザーから、この送信を実行できます。

この見本は、開発機の `127.0.0.1` で動かすためのものです。**公開の環境で使ってはいけません。**

## まとめ

この章では、次を確かめました。

- 独立した検証は、前の結果を受け取らない合成にすると、問題をまとめて返せる
- 問題の順序は利用者に見える仕様で、性質テストで固定する
- 入力の規則を厳しくするときは、保存済みの値を読めることを確かめる
- テンプレートの安全は、型で守れる部分と、守れない部分がある
- 2 つのストリームに触る操作も、第 10 章の仕組みで 1 回の更新にできる

`npm run check` は、テスト 188 ファイル 1563 件が通ります（2026-11-28、Windows、Node 22.23.4）。所要時間は約 59 秒で、**上限の 60 秒に届きかけています。** 章ごとに step を複製して、すべての step を検査しているためです。

次の章では、何が起きたかを後から追えるようにします。ログと、JSON との変換を扱います。

### 型で守れたこと・守れなかったこと

| 対象 | 型検査 | 守ったもの |
| :--- | :--- | :--- |
| 問題の種類を足して、文言を書かない | **止める** | `Record` の型 |
| テンプレートに、無いかもしれない値や、断片に似たオブジェクトを置く | **止める** | 型と、非公開の項目 |
| 問題を集めずに止まる、順序を逆にする | **通る** | 性質テストと受け入れシナリオ |
| 新しい名前が空いていることを確かめない | **通る** | domain と画面のテスト |
| 名前の変更で、片方のストリームだけを対象にする | **通る** | 保管の実行時の確認 |
| 保存済みの行に、入力の制限を掛ける | **通る** | 長い名前の契約のテスト（後から足した） |
| 別のサイトからの送信 | **通る** | **無い** |

### Effectに置き換えたこと・自作で残したこと

| 対象 | この章での扱い |
| :--- | :--- |
| 問題を集める検証 | 自作する。`Result` の失敗を配列にし、`zipWith` を書く。`Effect.validate` と `Schema` は使わない |
| 成功と失敗の入れ物 | Effect の `Result` を使う |
| テンプレート | 自作する（タグ付きテンプレート） |
| フォームの本文の読み取り | 自作の接続と、標準の `URLSearchParams` |
| 名前の変更 | 自作する（コマンドとイベント） |

## この章で書いたコード

- `apps/typescript-fp/zettai/zettai-step9-validation/src/domain/`（`validation.ts`、`todo-list.ts`、`handle.ts`、`streams.ts`、`events.ts`、`commands.ts`）
- `apps/typescript-fp/zettai/zettai-step9-validation/src/web/`（`template.ts`、`html.ts`、`app.ts`、`server.ts`）
- `apps/typescript-fp/zettai/zettai-step9-validation/test/`（`validation.test.ts`、`rename.test.ts`、`rename-form.test.ts`、`html-template.test.ts`）

## 参照

- Uberto Barbini『From Objects to Functions』第 11 章。章の題材と順序、アプリカティブで検証の問題を集める考え方は本書に拠っています。本連載のコードはすべて書き起こした自作実装です
- [Prettier の埋め込み言語の整形](https://prettier.io/docs/options#embedded-language-formatting)
- [OWASP の CSRF 対策](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)
- [Effect の公式サイト](https://effect.website/)
