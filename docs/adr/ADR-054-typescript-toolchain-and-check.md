---
type: ADR
title: "ADR-054 TypeScript FP 版は TypeScript 7 と @effect/tsgo を使い、npm run check を 5 段にする"
description: "Zettai 連載 TypeScript FP 版で、TypeScript 7.0.2・@effect/tsgo・Oxlint・Prettier を採り、tsc でビルドして Node で実行し、累積する step を npm workspaces で管理する決定。品質ゲート npm run check の各段が守るもの、実測した秒数、選ばなかった案と代償を記録する。"
tags: [adr, typescript, zettai, effect, testing]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-27T10:35:28Z }
---

# ADR-054 TypeScript FP 版は TypeScript 7 と @effect/tsgo を使い、npm run check を 5 段にする

日付: 2026-11-27

## ステータス

2026-11-27 承認されました（Unit 1 のゲート ② で、人が推奨の案を選びました）

## コンテキスト

TypeScript は型が実行時に消えます。また Effect は、作っただけでは動きません。型検査やテストが緑でも、書いた処理が動いていない状態が起こりえます。**偽の緑を止められる道具の組み合わせ**を、後続の全 Unit に先立って決める必要があります。

TypeScript の latest は 7.0.2 ですが、`typescript-eslint` 8.74.0 の peer は `typescript <6.1.0` です。Effect の診断は、TypeScript 7 以降は `@effect/tsgo`、それより前は `@effect/language-service` が受け持ちます。

### 先に確かめたこと

条件と全結果は、[Unit 1 の Bolt 計画](../development/iteration_plan-typescript-fp-1.md) の「S3〜S5 の結果」にあります。Windows の開発機と、ごく小さいコードでの結果です。

| 案 | 構成 | 6 種類の壊れたコード | 型検査・診断・lint・format・test の合計 |
| :--- | :--- | :--- | :--- |
| A | TypeScript 6.0.3、`@effect/language-service`、`typescript-eslint`、Prettier | 全部捕まえた | 10.8 秒 |
| **B（採用）** | TypeScript 7.0.2、`@effect/tsgo`、Oxlint（型情報あり）、Prettier | 全部捕まえた | 4.6 秒 |
| C | TypeScript 7.0.2、Biome | 型検査と lint で 3 種類を見逃した。実行していない `Effect` の 2 種類は、テストでも見つからなかった | 3.7 秒 |

- `tsc` だけでは、実行していない `Effect` を指摘しない
- vitest は、テストのコードの型エラーを見ない
- `tsc` でビルドして `node`、`tsx`、Node の型除去は、3 つとも実行できた

### 試していないこと

- Linux、Nix の devShell、Node 24 での実行
- 実際の規模のコードでの実行時間
- IDE（IntelliJ など）で TypeScript 7 と Effect の診断が使えるか
- TypeScript 7.0.2 と `typescript-eslint` の組み合わせ（peer の範囲外）
- 案 B の構成でのカバレッジの計測
- unstable な import を lint で止める設定
- 中の検証が成功する場合に、実行した Promise を捨てるコードを各道具が見つけるか

## 決定

1. **道具は次の版に固定する。** `typescript@7.0.2`、`@effect/tsgo@0.73.7`、`oxlint@1.95.0`、`oxlint-tsgolint@7.0.2008`、`prettier@3.10.1`。版を上げるときは、`@effect/tsgo` が対応する組で上げる
2. **`tsc` でビルドして `node` で実行する。** `tsx` は入れない。相対 import は `.ts` で書き、`rewriteRelativeImportExtensions` で出力を書き換える
3. **章の進みに合わせた `zettai-stepN-*` を積み重ね、npm workspaces で管理する**
4. **devShell と CI は Node 24 にする。** `engines` は `>=22.19.0` とし、開発機の Node 22 も許可する
5. **`npm run check` を次の 5 段にする。** CI も同じコマンドを実行する

| 順 | 段 | 守るもの | 秒数 |
| :--- | :--- | :--- | :--- |
| 1 | `effect-tsgo tsc` | テストを含む型、要求 `R` の解決漏れ、実行していない `Effect` | 1.4 |
| 2 | Oxlint（型情報あり。`no-floating-promises`） | 捨てた Promise と `await` 漏れ | 1.1 |
| 3 | Prettier | 体裁 | 0.7 |
| 4 | vitest | 振る舞い | 1.4 |
| 5 | ビルドして `node` で実行する smoke | テストは通るのに起動できない状態 | 1.4 |

秒数は 1〜4 が 3 回の中央値、5 が 1 回の測定です。**時間の上限はまだ置きません。** 雛形ができてから 3 回測り、この ADR に追記します。

6. **カバレッジは、今は入れない。** 基準値を決める根拠が無いためである。ドメインのコードができた後に見直す
7. **unstable な import を機械で止める段は、第 2 章で検討する。** それまで [ADR-053](ADR-053-typescript-effect-version-and-stability.md) の守りは、レビューに依る

### 検討した代替案

| 対象 | 案 | 採らなかった理由 |
| :--- | :--- | :--- |
| 道具 | A | 捕まえる範囲は同じで、時間が 2 倍以上かかる。TypeScript が 1 つ前のメジャー版になる |
| 道具 | C | 最も速いが、実行していない `Effect` を見逃す |
| 実行方法 | `tsx` | 依存が増え、ビルドした出力を確かめる段は別に必要になる |
| 実行方法 | Node の型除去 | 1 回の測定では速かったが、ビルドした出力を確かめられない |
| step の配置 | 単一の package | 後の章でコードが変わると、前の章の記事と一致しなくなる。Scala 版では、前の章のコードに検査の除外を付けた |
| Node | すべて 22 | 環境の差は小さいが、サポートの終了が 2027-04-30 で、24（2028-04-30）より 1 年早い |

[ADR-004](ADR-004-static-analysis.md)（Kotlin 版）は静的解析を `check` に入れませんでした。この版が lint と診断を入れるのは、**型検査とテストだけでは見つからない誤り**（実行していない `Effect`、捨てた Promise）を実測で確かめたためです。

## 影響

### ポジティブ

- 確かめた 6 種類の壊し方を、`check` で止められる
- 型検査と Effect の診断が 1 つの段で済む

### ネガティブ

- `@effect/tsgo` は 0.x で、対応する TypeScript の版が限られる
- IDE で同じ診断が表示されない可能性がある
- step を複製するため、修正が漏れる可能性がある。全 step を `check` の対象にする
- 開発機（Node 22）と CI（Node 24）で、結果が違う可能性がある

## コンプライアンス

- `apps/typescript-fp/zettai/` の `npm run check` が 5 段を実行し、CI も同じコマンドを使う
- [Unit 1 の Bolt 計画](../development/iteration_plan-typescript-fp-1.md) の「壊して確かめる項目」を、雛形に対して実行する

## 追記（2026-11-27、決定 7 について）

ゲート ② の後のスパイクで、**1 段目の `effect-tsgo tsc` が unstable な API の使用を `effect(unstableApiUsage)` として指摘し、終了コード 1 にする**ことを確かめました。unstable を止めるための段を、追加する必要はありません。

- 承認した unstable な API は、`// @effect-diagnostics unstableApiUsage:off`（ファイル全体）か、`// @effect-diagnostics-next-line unstableApiUsage:off`（使う行の直前）で除外します
- ファイル全体の除外は、そのファイルのほかの unstable な使用も通します
- 除外コメントが adapter の外にないことは、この段では確かめられません

## 追記（2026-11-27、雛形での実行時間）

雛形（step 1 つ、テスト 3 ファイル 9 件）で、`npm run` を使って 3 回ずつ測りました。Windows の開発機で、PostgreSQL のイメージは取得済みです。

| 対象 | 中央値 |
| :--- | :--- |
| `npm run check` 全体 | 11.4 秒（11.4・11.3・13.1） |
| `typecheck` | 1.8 秒 |
| `lint` | 1.2 秒 |
| `format:check` | 0.7 秒 |
| `test`（Testcontainers を含む） | 7.1 秒 |
| `smoke` | 2.4 秒 |

**時間の上限は、まだ決めていません。** CI で測ってから決めます。

## 追記（2026-11-27、Nix の devShell での確認）

`nixos/nix` のコンテナで、devShell `typescript-fp`（Node 24.12.0・npm 11.6.2）を作り、型検査・lint・format・smoke と、PostgreSQL のテストを除くテストが通ることを確かめました。PostgreSQL のテストは、コンテナにホストの Docker のソケットを渡さなかったため、実行していません。

- 共通の shell の Node 22 は、パッケージ名で外します。`buildInputs` の要素は `dev` 出力になり、パッケージそのものとの比較は一致しません
- Oxlint の除外は、`.gitignore` に頼らず `.oxlintrc.json` に書きます。`.gitignore` が無い場所では、`node_modules` まで検査しました

## 追記（2026-11-27、CI での実行時間）

コミット `8cf2565` の CI（`ubuntu-latest`、Node 24.22.3）の 1 回の実行では、`npm ci --ignore-scripts` が 7 秒、`npm run check` が 17 秒でした。テストの段は 12.98 秒で、PostgreSQL のイメージの取得を含みます。ステップの開始と終了の時刻（秒の単位）と、vitest の表示から読みました。**1 回の測定です。時間の上限は、まだ決めていません。**

## 追記（2026-11-27、実行時間の上限）

`npm run check` の上限を、開発機と CI のどちらも **60 秒**にします（人が承認しました）。超えたら、段ごとに測って原因を調べます。承認の時点の実測は、第 2 章の step を足した開発機で中央値 14.3 秒（3 回）、第 1 章までの CI で 18 秒（1 回）です。**上限を機械で確かめる手段はありません。** 各 Unit で測ります。

## 備考

- 著者: anthropic/claude-sonnet-5-5
- 関連 ADR: ADR-053、ADR-055（予定。テストの道具）
