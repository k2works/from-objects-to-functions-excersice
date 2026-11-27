# Zettai（TypeScript FP 版）

TypeScript と Effect で書く Zettai のサンプル実装です。記事は `docs/article/zettai/typescript-fp/` に置きます。

**準備中です。** CI は `.github/workflows/typescript-fp-zettai.yml` です。Nix を使う場合は、リポジトリの直下で `nix develop .#typescript-fp` を実行します。進め方は [Unit 1 の Bolt 計画](../../../docs/development/iteration_plan-typescript-fp-1.md) にあります。

## 実行方法

Node 22.19 以上と Docker が必要です。次のコマンドは、このディレクトリで実行します。

```bash
npm ci --ignore-scripts
npm run check
```

`npm run check` は、型検査と Effect の診断、lint、format の確認、テスト、ビルドした出力の実行を順に行います（[ADR-054](../../../docs/adr/ADR-054-typescript-toolchain-and-check.md)）。テストは Testcontainers で PostgreSQL を起動します。Docker に接続できない場合は、skip せずに失敗します。

## 構成

| step | 内容 |
| :--- | :--- |
| `zettai-step1-foundation` | 前提整備と第 1 章。基盤を確かめる最小のテスト |

`two-routes.test.ts` と `postgres.smoke.test.ts` は**基盤の確認用**です。Zettai の業務シナリオではありません。2 経路は、関数を直接呼ぶ経路と、`Request`・`Response` を通す経路で、サーバーは起動しません。

## 既知の制約

踏んだその場で足します。

### 言語・実行環境

| # | 制約 | 影響 | 回避策・守り方 | 見つけた Unit |
| :--- | :--- | :--- | :--- | :--- |
| 1 | Nix の `buildInputs` の要素は `dev` 出力になり、`packages.nodejs_22` との比較は一致しない | 共通の shell の Node 22 が残り、Node 24 が使われない | パッケージ名で外す。devShell の中で `node --version` を確かめる | 1 |
| 2 | Oxlint の対象の除外を `.gitignore` に頼ると、`.gitignore` が無い場所では `node_modules` まで検査する | lint が依存のコードで失敗する | `.oxlintrc.json` の `ignorePatterns` に書く | 1 |
| 3 | Git の `core.autocrlf=true` では、取り出したファイルが CRLF になる | Windows で `format:check` が失敗する | `.gitattributes` で、`apps/typescript-fp/` を `eol=lf` にする | 1 |

### Effect

| # | 制約 | 影響 | 回避策・守り方 | 見つけた Unit |
| :--- | :--- | :--- | :--- | :--- |
| 1 | reject する Promise は、`Effect.promise` で包むと defect（`Die`）になり、型付きエラーにならない（4.1.6 で確認） | 失敗が型の `E` に現れない | 失敗しうる Promise は `Effect.tryPromise` で包む | 1 |

### 型検査と lint が捕まえないもの

| # | 捕まえないもの | 影響 | 回避策・守り方 | 見つけた Unit |
| :--- | :--- | :--- | :--- | :--- |
| 1 | vitest は、テストのコードの型エラーを見ない | 型の主張が壊れてもテストが通る | `npm run check` で、テストも型検査する | 1 |
| 2 | `tsc` だけでは、実行も合成もしていない `Effect` を指摘しない | 書いた処理が動かないまま通る | `effect-tsgo tsc` を `npm run check` に入れる（ADR-054） | 1 |
| 3 | `@ts-expect-error` は、Effect の診断を抑えない | 診断で拒否される書き方を、`@ts-expect-error` では固定できない | `// @effect-diagnostics-next-line <診断名>:off` を使う。指摘が出なくなると、型検査が失敗する | 1 |
| 4 | `Effect` の外で実行した副作用は、型に現れない。純粋な関数に足した `console.log` は、型検査・lint・テストを通った | 純粋さと遅延実行が、型では守られない | 遅延実行はテストで固定する。純粋さは規約とレビューで守る | 1 |
| 5 | `@ts-expect-error` で書き始めたコメントは、説明文でも指定として扱われる | 説明のつもりのコメントで型検査が失敗する | 説明のコメントを、指定の名前で書き始めない | 1 |
