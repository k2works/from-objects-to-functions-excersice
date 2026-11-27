---
type: Bolt Report
title: "Unit 1 の Bolt 終了報告 - Zettai 連載（TypeScript FP 版）"
description: "TypeScript FP 版 Unit 1 の品質ゲート、CI、記事の検査、第 1 章の成果と指標を記録する。第 1 章は人の承認を受け、push 後の CI が残っている。"
tags: [development, report, ai-dlc, zettai, typescript, effect, unit-1]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-27T10:05:45Z }
---

# Unit 1 の Bolt 終了報告 - Zettai 連載（TypeScript FP 版）

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Unit | 1：土台と第 1 章 |
| Bolt | 1-1 前提整備 / 1-2 第 1 章 |
| 実施日 | 2026-11-27 |
| 状態 | **未完了。** 作業は終わり、下の「残り」の 1 件を待っている |

計画は [Unit 1 の Bolt 計画](iteration_plan-typescript-fp-1.md) にあります。

## 残り

| # | 残り | 待っているもの |
| :--- | :--- | :--- |
| 1 | 第 1 章のコードを含むコミットで、CI が成功すること | push の指示 |

第 1 章は、2026-11-27 に人の承認を受けました。

## 成果

- `apps/typescript-fp/zettai/` に、5 段の `npm run check` を持つ npm workspaces を作りました
- devShell `typescript-fp` と CI を追加しました
- 記事の検査 3 本を TypeScript に対応させました
- ADR-053〜055 で、Effect の版と安定性の扱い、道具と品質ゲート、テストの道具を決めました
- [第 1 章](../article/zettai/typescript-fp/chapter01.md) を書きました

## 品質結果

| ゲート | 結果 | 対象 |
| :--- | :--- | :--- |
| `npm run check`（開発機の Windows、Node 22.23.4） | 3 回続けて成功。7 ファイル 14 件 | 第 1 章を含む作業ツリー |
| GitHub Actions `TypeScript FP Zettai` | [成功](https://github.com/k2works/from-objects-to-functions-excersice/actions/runs/45976313499)。9 件 | **雛形のコミット `8cf2565`。第 1 章のコードは含まない** |
| Nix の devShell（Docker 上、Node 24.12.0） | PostgreSQL のテストを除いて成功 | 雛形 |
| 記事の検査 5 本 | 違反 0 件（記事 79 ファイル） | 第 1 章を含む作業ツリー |
| `mkdocs build --strict`（手元、PlantUML は一時コンテナ） | 成功 | 同上 |
| OKF の検査 | 本 Unit の変更による ERROR は無い。着手前からの ERROR が 1 件 | 同上 |
| 開発成果物レビュー | **実施していない** | — |

## 仮説の確認

> Effect v4 で、型検査・テスト・Node での実行が 1 つのゲートにまとまる。

**まとまりました。** ただし、型検査の段は素の `tsc` ではなく `@effect/tsgo` が必要でした。素の `tsc` は、実行していない `Effect` を指摘しません。また、`Effect` の外で実行する副作用と、純粋な関数の中の `console.log` は、型検査と lint を通りました。

## 指標

| 指標 | 計画 | 実績 |
| :--- | :--- | :--- |
| ステップ | 17 | 17 |
| 承認ゲートの同期停止 | 3 箇所中 2 箇所以上 | **3 / 3** |
| ゲートの変更依頼数 | — | 0 |
| ゲートのほかに停止した箇所 | — | 1（CI と実行環境の追加の前。必ず停止する操作） |
| 人が修正を入れた箇所数（コミット後の手直し） | — | 0（この報告の時点） |
| リードタイム | — | 未確定。最初のコミットは 2026-11-27 18:30（`4910e8d`）で、完了のコミットがまだ無い |
| 公開する章 | 1 | 1（コミット前） |
| ADR | 3 | 3 |

## `npm run check` の時間

開発機で、`npm run` を使って 3 回ずつ測りました。PostgreSQL のイメージは取得済みです。

| 構成 | 全体（中央値と各回） | `test` の中央値 |
| :--- | :--- | :--- |
| 雛形（3 ファイル 9 件） | 11.4 秒（11.4・11.3・13.1） | 7.1 秒 |
| 第 1 章を含む（7 ファイル 14 件） | 22.6 秒（22.6・17.1・26.1） | 15.2 秒 |

CI の 1 回の実行（雛形）では、`npm run check` が 17 秒でした。**開発機で時間が延び、ばらついた原因は調べていません。時間の上限は決めていません。**

## 計画から変えたこと

| 変えたこと | 理由 |
| :--- | :--- |
| `shell.nix` の Node の外し方を、パッケージの比較から名前に変えた | 承認した内容では、devShell の Node が 22 のままだった |
| `.gitattributes` を変更した（計画の「変更する既存ファイル」に無い） | `core.autocrlf=true` の環境で、取り出し直すと `format:check` が失敗した |
| `.oxlintrc.json` に `node_modules` の除外を足した | `.gitignore` が無い場所で、lint が依存まで検査した |
| 型で拒否される例の固定に、`@effect-diagnostics-next-line` を使う規約を足した | `@ts-expect-error` は、Effect の診断を抑えない |
| 雛形の `sum` と `parsePositiveInt` を、第 1 章の例に置き換えた | 記事で説明しないコードを残さないため |

## 確かめていないこと

| # | 確かめていないこと |
| :--- | :--- |
| 1 | 第 1 章のコードの、Linux と Node 24 での実行（push 後の CI で確かめる） |
| 2 | Nix の devShell での PostgreSQL のテスト。コンテナにホストの Docker のソケットを渡さなかった |
| 3 | macOS と、Windows の Nix |
| 4 | IDE で TypeScript 7 と Effect の診断が使えるか |
| 5 | 移行ガイドの `schema.md` の内容。`v3-to-v4.md` は一部を検索しただけ |
| 6 | 依存の脆弱性の検査（`npm audit`）と、Markdown Lint |
| 7 | `console` の呼び出しを禁じる lint のルール |

## 次の Unit への持ち越し

- `check` の時間の上限を決める。ばらつきの原因を調べるかも判断する
- unstable な API の除外コメントが adapter の中だけにあることを、どう守るかを決める
- `effect-tsgo` の suggestion（`foundation.test.ts` の `Effect.provideService`）が、毎回の出力に出る。第 4 章で `Layer` を扱うときに判断する
- 2 経路の基盤確認用テストを、第 2〜3 章の Zettai の業務シナリオに置き換える
