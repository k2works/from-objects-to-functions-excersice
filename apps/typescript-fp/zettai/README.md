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

サーバーは、次のコマンドで起動します。ポートは環境変数 `ZETTAI_PORT` で変えられます（指定が無ければ 8080 番）。

```bash
cd zettai-step2-http-domain
ZETTAI_PORT=18123 npm run start
```

## 構成

| step | 内容 |
| :--- | :--- |
| `zettai-step1-foundation` | 前提整備と第 1 章。基盤を確かめる最小のテスト |
| `zettai-step2-http-domain` | 第 2〜3 章。HTTP の最小の縦串（`GET /todo/{user}/{listname}`）と、domain / HTTP の 2 経路の受け入れテスト（`test/acceptance/`） |

`two-routes.test.ts` と `postgres.smoke.test.ts` は**基盤の確認用**です。Zettai の業務シナリオではありません。2 経路は、関数を直接呼ぶ経路と、`Request`・`Response` を通す経路で、サーバーは起動しません。

## 既知の制約

踏んだその場で足します。

### 言語・実行環境

| # | 制約 | 影響 | 回避策・守り方 | 見つけた Unit |
| :--- | :--- | :--- | :--- | :--- |
| 1 | Nix の `buildInputs` の要素は `dev` 出力になり、`packages.nodejs_22` との比較は一致しない | 共通の shell の Node 22 が残り、Node 24 が使われない | パッケージ名で外す。devShell の中で `node --version` を確かめる | 1 |
| 2 | Oxlint の対象の除外を `.gitignore` に頼ると、`.gitignore` が無い場所では `node_modules` まで検査する | lint が依存のコードで失敗する | `.oxlintrc.json` の `ignorePatterns` に書く | 1 |
| 3 | Git の `core.autocrlf=true` では、取り出したファイルが CRLF になる | Windows で `format:check` が失敗する | `.gitattributes` で、`apps/typescript-fp/` を `eol=lf` にする | 1 |

### HTTP（第 2 章の実装）

| # | 制約 | 影響 | 回避策・守り方 | 見つけた Unit |
| :--- | :--- | :--- | :--- | :--- |
| 1 | 接続の自作は、`GET` と本文の無い要求だけを扱う | フォームの POST を受けられない | 本文の読み取りを、必要になる章で足す | 2 |
| 2 | 500 の原因を、どこにも記録しない | 失敗の調査ができない | 第 12 章で記録を足す | 2 |
| 3 | 本番で使える HTTP サーバーではない。性能、同時の要求、大きな要求は確かめていない | 公開する用途には使えない | 学習用の範囲で使う | 2 |
| 4 | シグナルを受けて停止の処理を実行する仕組みが無い | `Ctrl+C` で終了したときに `close` が実行されるかは確かめていない | 必要になる章で扱う | 2 |
| 5 | `new URL(path, base)` は、`//` で始まるパスをホスト名と解釈する | 別のパスとして扱われる | パスは origin の後ろに連結する。テストで固定した | 2 |
| 6 | `new Request` は `TRACE` に例外を投げる。callback の中で投げると、応答が返らない | 要求がタイムアウトする | `GET` 以外は `Request` を作る前に返す。テストで固定した | 2 |
| 7 | 開発機（Windows）では、8080 番の待ち受けが `EACCES` で失敗した | 既定のポートで起動できないことがある | `ZETTAI_PORT` で変える | 2 |
| 8 | Windows の深いディレクトリにコピーすると、smoke の段で `tsc` が見つからなかった。原因は確かめていない | `npm run check` が失敗する | 浅いディレクトリに clone する（この回避策は試していない） | 2 |

### 受け入れテスト（第 3 章）

| # | 制約 | 影響 | 回避策・守り方 | 見つけた Unit |
| :--- | :--- | :--- | :--- | :--- |
| 1 | シナリオの操作の `await` を忘れると、vitest は件数の上では全件成功と表示する（終了コードは失敗） | 件数の行だけを見ると見落とす | lint の `no-floating-promises` が指摘する。終了コードで判定する | 2 |
| 2 | HTTP の経路の読み戻しは HTML を解析せず、`<h1>` と `<li>` を探す | 画面の作りを変えると、読み戻しを直す必要がある | 読み戻しを 1 か所にまとめ、テストを持つ | 2 |
| 3 | 経路で、閉じないスコープを渡すと、型検査と lint を通る | サーバーが停止しない | 経路の後始末をテストする（`routes.test.ts`） | 2 |
| 4 | 状態を変える操作を含むシナリオは、まだ無い | 経路の形が、状態を扱えるかは確かめていない | 第 6 章で確かめる | 2 |

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
