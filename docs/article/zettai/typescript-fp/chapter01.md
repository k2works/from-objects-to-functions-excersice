---
type: Article
title: "第 1 章 新しいアプリケーションを準備する"
description: "Zettai 連載 TypeScript FP 版の第 1 章。TypeScript と Effect v4 の品質ゲートを作り、純粋関数、値としての失敗（Result）、値としての副作用（Effect）をテストファーストで導く。テストが通っても型エラーや実行し忘れた Effect が残ること、型では守れない副作用があることを実際に壊して確かめる。"
tags: [article, zettai, typescript, effect, chapter]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-27T10:00:22Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-27T10:05:46Z }
---

# 第 1 章 新しいアプリケーションを準備する

この連載は、同じ 13 章を複数の言語で書き起こしています。この版は **TypeScript と Effect** で書きます。

TypeScript の型は、実行時には残りません。型付けは構造的で、形が同じ型は区別されません。そして言語には、副作用や失敗を型で追いかける仕組みがありません。Effect は、それらを `Effect<A, E, R>` という型に載せるライブラリです。`A` は成功の値、`E` は失敗の型、`R` は実行に必要な依存の型です。

この版で問うのは、**型に載せると、設計のどこが型で守られ、どこが規約とテストに残るのか**です。

## サンプルアプリケーションを定義する

題材は、ほかの対象と同じ **Zettai** という ToDo リストアプリケーションです。Uberto Barbini の『From Objects to Functions』から借りています。13 章をかけて、HTTP でリストを見せるところから、イベントソーシング、CQRS、永続化、バリデーション、監視までを作ります。**設計はほかの対象と同じで、変わるのは実現手段です。**

この章では、これから使う次の 3 つを確かめます。

- 計算は、副作用を持たない純粋関数に置く
- 失敗は、例外ではなく値にする
- 副作用は `Effect` の値として組み立て、実行は外側に寄せる

## Zettai: イノベーティブなToDoリストアプリケーション

Zettai は、複数の ToDo リストと、その中の項目を扱います。項目には、説明、任意の期限、状態があります。この章では、まだ実装しません。

最初の例には、単語の得点を使います。単語を受け取り、文字数を得点として返す関数です。小さい例ですが、純粋関数の性質を確かめるには足ります。

## テストに開発をガイドさせる

この連載では、Red → Green → Refactor の順序を守ります。

1. 望む振る舞いをテストに書く
2. 失敗を目で確かめる
3. 通すための最小の実装を書く
4. テストを通したまま、名前と構造を整える

テストは 3 本書きます。具体例が 1 本と、性質が 2 本です。

```typescript
import fc from "fast-check"
import { describe, expect, it } from "vitest"
import { wordScore } from "../src/word-score.ts"

const TRIALS = 200

describe("wordScore", () => {
  it("scores a word by its length", () => {
    expect(wordScore("TypeScript")).toBe(10)
  })

  it("gives the same score for the same word", () => {
    expect(wordScore("TypeScript")).toBe(wordScore("TypeScript"))
  })

  it("scores two joined words as the sum of their scores", () => {
    fc.assert(
      fc.property(fc.string(), fc.string(), (left, right) => {
        expect(wordScore(left + right)).toBe(wordScore(left) + wordScore(right))
      }),
      { numRuns: TRIALS }
    )
  })
})
```

3 本目は**性質テスト**です。`fast-check` が文字列の組を作り、どの組でも性質が成り立つかを確かめます。試行回数は 200 回です。

Red を確かめるために、型だけを合わせて、常に 0 を返す実装を置きます。

<!-- code-check: ignore Red を確かめるための仮の実装 -->
```typescript
export const wordScore = (_word: string): number => 0
```

`npx vitest run word-score` の結果は、次のとおりです（抜粋）。

```bash
     × scores a word by its length 12ms
AssertionError: expected +0 to be 10 // Object.is equality
      Tests  1 failed | 2 passed (3)
```

**失敗したのは、具体例の 1 本だけです。** 「同じ単語には同じ得点」も、「連結した単語の得点は、それぞれの得点の和」も、常に 0 を返す関数で成り立ちます。性質は、正しい実装なら満たすべき条件ですが、その条件だけでは正しい実装を決められないことがあります。この連載では、性質テストと具体例を組にして書きます。

Green にする実装は、次の 1 行です。

```typescript
export const wordScore = (word: string): number => word.length
```

同じ文字列には、常に同じ整数を返し、外の状態を読み書きしません。`wordScore("TypeScript")` という式は、いつでも `10` に置き換えられます。置き換えてもプログラムの意味が変わらないこの性質を、**参照透過性**と呼びます。

## プロジェクトをセットアップする

### 版を固定する

道具の版は、次のように固定します。

| 道具 | 版 | 役割 |
| :--- | :--- | :--- |
| Effect | 4.1.6 | 中心のライブラリ |
| TypeScript | 7.0.2 | 型検査とビルド |
| `@effect/tsgo` | 0.73.7 | 型検査に、Effect の診断を加える |
| Oxlint / `oxlint-tsgolint` | 1.95.0 / 7.0.2008 | 型情報を使う lint |
| Prettier | 3.10.1 | 整形 |
| vitest / `fast-check` | 5.1.2 / 4.11.0 | テストと性質テスト |

Effect の 4 系は、3 系と API が違います。たとえば、3 系の `Either` は `Result` になりました。Web で見つかるコードや説明には、3 系を前提にしたものがあります。この連載のコードは、4.1.6 で動かしたものです。

Effect の API には、安定性の区別があります。unstable とされる API は、minor リリースで壊れる変更が入る可能性があります。この版は stable な API を原則にし、unstable な API は、代替と比べて認めたものだけを使います（[ADR-053](../../../adr/ADR-053-typescript-effect-version-and-stability.md)）。**この章で使う Effect の API は、stable なものだけです。**

### 構成

章の進みに合わせて step を積み重ね、npm の workspaces で管理します。

```text
apps/typescript-fp/zettai/
├── package.json            品質ゲートと、開発に使う道具
├── tsconfig.base.json      すべての step で共通の型検査の設定
├── tsconfig.json           テストを含めた型検査の対象
└── zettai-step1-foundation/
    ├── package.json        実行時の依存（effect）
    ├── src/
    └── test/
```

型検査の設定は次のとおりです。`strict` に加えて、見落としを減らす項目を有効にします。`plugins` は、Effect の診断を指定します。

```json
{
  "compilerOptions": {
    "target": "es2023",
    "module": "nodenext",
    "strict": true,
    "exactOptionalPropertyTypes": true,
    "noUncheckedIndexedAccess": true,
    "verbatimModuleSyntax": true,
    "rewriteRelativeImportExtensions": true,
    "types": ["node"],
    "plugins": [{ "name": "@effect/language-service" }]
  }
}
```

相対 import は、`../src/word-score.ts` のように `.ts` で書きます。`rewriteRelativeImportExtensions` が、ビルドした出力では `.js` に書き換えます。

### 品質ゲートは 1 つのコマンドにする

```json
    "check": "npm run typecheck && npm run lint && npm run format:check && npm run test && npm run smoke",
    "typecheck": "effect-tsgo tsc -p tsconfig.json",
    "lint": "effect-tsgo oxlint --type-aware",
```

| 段 | 守るもの |
| :--- | :--- |
| `typecheck` | テストを含む型、依存の解決漏れ、実行していない `Effect`、unstable な API の使用 |
| `lint` | 待っていない Promise |
| `format:check` | 整形 |
| `test` | 振る舞い |
| `smoke` | ビルドした出力が Node で起動できること |

段を分けた理由は、後の「型で守れたこと・守れなかったこと」で確かめます。道具の組を選んだ過程は [ADR-054](../../../adr/ADR-054-typescript-toolchain-and-check.md) にあります。

### 実行する

Node 22.19 以上と Docker を用意して、次を実行します。テストは Testcontainers で PostgreSQL を起動します。Docker に接続できない場合は、テストを飛ばさずに失敗します。

```bash
cd apps/typescript-fp/zettai
npm ci --ignore-scripts
npm run check
```

`--ignore-scripts` は、依存の package が用意したインストール時のスクリプトを実行しない指定です。

Nix を使う場合は、リポジトリの直下で devShell に入ります。devShell は Node 24 を提供します。定義は次のとおりです。

```nix
{ packages ? import <nixpkgs> {} }:
let
  baseShell = import ../../shells/shell.nix { inherit packages; };
  # 共通 shell の Node を外し、Node 24 だけにする。
  # buildInputs の要素は dev 出力になり、packages.nodejs_22 との比較は一致しないため、名前で外す。
  isNode = p: packages.lib.getName p == "nodejs";
  baseInputs = builtins.filter (p: !(isNode p)) baseShell.buildInputs;
in
packages.mkShell {
  inherit (baseShell) pure;
  buildInputs = baseInputs ++ [ packages.nodejs_24 ];
  shellHook = ''
    ${baseShell.shellHook}
    echo "TypeScript FP development environment activated"
    echo "  - Node.js: $(node --version)"
    echo "  - npm: $(npm --version)"
  '';
}
```

```bash
nix develop .#typescript-fp
```

コメントにある除外の書き方は、実際につまずいた結果です。最初は `packages.nodejs_22` と比べて外そうとしましたが、devShell の中の Node は 22 のままでした。**devShell を作ったら、中で `node --version` を確かめてください。**

確かめた環境は、次のとおりです（2026-11-27）。

| 環境 | 範囲 |
| :--- | :--- |
| Windows、Node 22.23.4 | この章のコードで `npm run check` が通る |
| GitHub Actions（`ubuntu-latest`）、Node 24.22.3 | この章のコードを書く前の雛形で `npm run check` が通った |
| `nixos/nix` のコンテナ、Node 24.12.0 | 雛形で、PostgreSQL のテストを除いて通った |

macOS と、Windows の Nix では確かめていません。

## ユニットテストを関数型にする

純粋関数のテストには、共有する可変の fixture も、mock も要りません。入力を用意して、出力を比べるだけです。ここからは、同じ形を失敗と副作用に広げます。

### 失敗も値にする

空の名前は、例外を投げる代わりに、失敗の値を返します。Effect の `Result` は、成功か失敗のどちらかを表す値です。テストを先に書きます。

```typescript
  it("keeps a usable name as a success", () => {
    expect(nonBlankName("仕事")).toEqual(Result.succeed("仕事"))
  })

  it("returns a blank name as a failure value, not an exception", () => {
    expect(nonBlankName("  ")).toEqual(Result.fail("名前は空にできません"))
  })
```

どの名前も成功にする仮の実装では、2 本目が失敗します（抜粋）。

```bash
AssertionError: expected { Object (success) } to deeply equal { Object (failure) }
```

最小の実装は、次のとおりです。

```typescript
import { Result } from "effect"

export const nonBlankName = (name: string): Result.Result<string, string> =>
  name.trim() === "" ? Result.fail("名前は空にできません") : Result.succeed(name)
```

戻り値の型 `Result.Result<string, string>` を見れば、失敗しうることが分かります。第 7 章で、失敗の型の設計を改めて扱います。

### Promise は作った時点で動く

次は副作用です。先に、TypeScript の `Promise` の振る舞いを、テストで固定します。

```typescript
  it("runs as soon as it is created, and only once", async () => {
    let runs = 0
    const promise = new Promise<void>((resolve) => {
      runs += 1
      resolve()
    })
    expect(runs).toBe(1)

    await promise
    await promise
    expect(runs).toBe(1)
  })
```

`Promise` は、**作った時点で処理が始まり、何度待っても 1 回しか実行されません。** `Promise` の値は「実行中か、実行し終えた処理の結果」で、「これから実行する手順」ではありません。

### Effect は実行するまで動かない

`Effect` は、実行する手順を値にします。単語の得点を求めて、その結果を記録する `announceScore` を作ります。記録先は引数で受け取るので、テストは配列に記録できます。

```typescript
  it("does nothing until the Effect is run, and runs every time", async () => {
    const lines: Array<string> = []
    const announce = announceScore("Effect", (line) => {
      lines.push(line)
    })
    expect(lines).toEqual([])

    expect(await Effect.runPromise(announce)).toBe(6)
    expect(lines).toEqual(["Effect: 6"])

    await Effect.runPromise(announce)
    expect(lines).toEqual(["Effect: 6", "Effect: 6"])
  })
```

このテストは、作っただけでは何も起きないことと、実行するたびに 1 回起きることを確かめます。

仮の実装には、起こりやすい誤りを選びます。戻り値は `Effect` ですが、記録は `Effect` の外で行います。

<!-- code-check: ignore Red を確かめるための誤った実装 -->
```typescript
export const announceScore = (word: string, record: (line: string) => void) => {
  const score = wordScore(word)
  record(`${word}: ${score}`)
  return Effect.succeed(score)
}
```

テストは失敗します（抜粋）。

```bash
AssertionError: expected [ 'Effect: 6' ] to deeply equal []
```

一方で、**`npm run typecheck` は、この誤った実装を通しました。** 戻り値の型は正しいためです。`Effect` の型は、`Effect` の外で実行した処理を表しません。この誤りを見つけたのは、テストでした。

Green にするには、記録を `Effect.sync` の中に移します。

```typescript
export const announceScore = (word: string, record: (line: string) => void) =>
  Effect.sync(() => {
    const score = wordScore(word)
    record(`${word}: ${score}`)
    return score
  })
```

`Effect.sync` に渡した関数は、その `Effect` を実行したときに呼ばれます。テストでは `Effect.runPromise` で実行します。アプリケーションの側では、実行を一番外側の 1 か所に置きます。いまは、ビルドした出力の起動を確かめる `main.ts` がその場所です。

```typescript
const program = announceScore("Zettai", (line) => {
  console.log(line)
})

await Effect.runPromise(program)
```

## まとめ

この章では、品質ゲート `npm run check` を作り、次を確かめました。

- 純粋関数は、同じ入力に同じ出力を返し、参照透過である
- 性質テストは、常に 0 を返す実装でも通ることがある。具体例と組にする
- 失敗は `Result` の値で表せる
- `Promise` は作った時点で動き、`Effect` は実行するまで動かない
- `Effect` の外に書いた副作用は、型検査を通る

### 型で守れたこと・守れなかったこと

コードをわざと壊して、どの段が止めるかを確かめました（Windows、Node 22.23.4）。

**テストが通っても、型エラーは残ります。** 次のテストは、数値を `string` の変数に入れています。

<!-- code-check: ignore わざと壊したコード -->
```typescript
it("compares a score with a string", () => {
  const expected: string = wordScore("Effect")
  expect(expected).toBe(6)
})
```

vitest は成功し、型検査が止めます。vitest は、テストのコードの型を検査しません。

```bash
zettai-step1-foundation/test/broken.test.ts(5,9): error TS2322: Type 'number' is not assignable to type 'string'.
```

**実行し忘れた `Effect` は、型検査の段が止めます。** 次のテストは、呼ばれたら例外を投げる記録先を渡していますが、`Effect` を実行していません。

<!-- code-check: ignore わざと壊したコード -->
```typescript
it("announces the score", () => {
  announceScore("Effect", (line) => {
    throw new Error(line)
  })
})
```

例外は投げられず、**vitest は成功します。** 止めたのは、`@effect/tsgo` の診断です。

```bash
zettai-step1-foundation/test/broken.test.ts(5,3): error TS377001: This Effect value is neither yielded nor used in an assignment. effect(floatingEffect)
```

文として捨てた `Effect` を素の `tsc` で型検査しても、指摘は出ませんでした。道具を選んだときに、小さなコードで確かめた結果です。

**拒否され続けることも、テストで固定します。** 道具の版や設定が変わって指摘が出なくなると、この章の説明は気づかないうちに誤りになります。型エラーには、`@ts-expect-error` を使います。次の行がエラーにならなくなると、`@ts-expect-error` そのものがエラーになります。

```typescript
  it("does not allow a score to be used as a string", () => {
    // @ts-expect-error 得点は number なので、string の変数には入れられない
    const score: string = wordScore("Effect")
    expect(score).toBe(6)
  })
```

Effect の診断は、`@ts-expect-error` では抑えられませんでした。代わりに、診断を指定して除外するコメントを使います。指摘が出なくなると、`@effect-diagnostics directive has no effect.` という警告が出て、型検査が失敗します。

```typescript
  it("does not allow an Effect to be left unrun", () => {
    const lines: Array<string> = []
    // @effect-diagnostics-next-line floatingEffect:off
    announceScore("Effect", (line) => {
      lines.push(line)
    })
    expect(lines).toEqual([])
  })
```

どちらも、拒否されない書き方に変えると型検査が失敗することを確かめました。

**純粋なはずの関数の副作用は、止まりません。** `wordScore` に `console.log` を足します。

<!-- code-check: ignore わざと壊したコード -->
```typescript
export const wordScore = (word: string): number => {
  console.log("scoring")
  return word.length
}
```

**型検査も、lint も、テストも、すべて成功しました。** 型は `(word: string) => number` のままで、副作用があることを表しません。いまの品質ゲートでは、純粋な関数を純粋に保つことは、規約とレビューに残ります。`console` の呼び出しを lint のルールで禁じる方法は、試していません。試したとしても、それで止められるのは `console` の呼び出しで、副作用のすべてではありません。

| 壊し方 | 型検査 | テスト |
| :--- | :--- | :--- |
| テストの型エラー | **止めた** | 通った |
| 実行し忘れた `Effect` | **止めた** | 通った |
| `Effect` の外で実行する誤った実装 | 通った | **止めた** |
| 純粋な関数に足した `console.log` | 通った | 通った |

表の 3 行目は、Red を確かめた `announceScore` の仮の実装です。

### Effectに置き換えたこと・自作で残したこと

| 対象 | この章での扱い |
| :--- | :--- |
| 成功か失敗かを表す型 | Effect の `Result` を使う。自作しない |
| 後で実行する手順を表す型 | Effect の `Effect` を使う。自作しない |
| 受け取った値をそのまま返す計算 | TypeScript の関数のまま書く。`Effect` で包まない |
| テストでの `Effect` の実行 | `Effect.runPromise` を自分で呼ぶ。unstable な `@effect/vitest` は使わない（[ADR-055](../../../adr/ADR-055-typescript-testing-tools.md)） |

雛形には、この章で説明しないコードもあります。`Layer` で依存を差し替えるテスト（第 4 章で扱います）と、2 経路・PostgreSQL の起動を確かめる基盤確認用のテストです。基盤確認用のテストは、Zettai の業務シナリオではありません。

次の章では、純粋な核と HTTP の殻を分けたまま、Zettai の最小の縦串を通します。HTTP の実装方式は、unstable な `effect/http` と代替を比べて決めます。

## この章で書いたコード

- `apps/typescript-fp/zettai/package.json`
- `apps/typescript-fp/zettai/tsconfig.base.json`
- `apps/typescript-fp/zettai/zettai-step1-foundation/src/word-score.ts`
- `apps/typescript-fp/zettai/zettai-step1-foundation/src/list-name.ts`
- `apps/typescript-fp/zettai/zettai-step1-foundation/src/announce-score.ts`
- `apps/typescript-fp/zettai/zettai-step1-foundation/src/main.ts`
- `apps/typescript-fp/zettai/zettai-step1-foundation/test/word-score.test.ts`
- `apps/typescript-fp/zettai/zettai-step1-foundation/test/list-name.test.ts`
- `apps/typescript-fp/zettai/zettai-step1-foundation/test/announce-score.test.ts`
- `apps/typescript-fp/zettai/zettai-step1-foundation/test/type-guards.test.ts`
- `ops/nix/environments/typescript-fp/shell.nix`

## 参照

- Uberto Barbini『From Objects to Functions』第 1 章。題材の設定は本書に拠っています。本連載のコードはすべて書き起こした自作実装です
- Michał Płachta『Grokking Functional Programming』。単語の得点という例は本書に拠っています
- [Effect 公式サイト](https://effect.website/) と [v3 から v4 への移行ガイド](https://github.com/Effect-TS/effect/blob/main/MIGRATION.md)
- [vitest](https://vitest.dev/) / [fast-check](https://fast-check.dev/)
