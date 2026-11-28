---
type: Bolt Report
title: "Unit 5 の Bolt 終了報告 - Zettai 連載（TypeScript FP 版）"
description: "TypeScript FP 版 Unit 5 の射影、CQRS、PostgreSQL への永続化の成果と、壊して分かった限界。人の全文確認と CI が残り、完了条件は未達。"
tags: [development, report, ai-dlc, zettai, typescript, effect, unit-5]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-28T05:22:54Z }
---

# Unit 5 の Bolt 終了報告 - Zettai 連載（TypeScript FP 版）

## 基本情報

| 項目 | 内容 |
| :--- | :--- |
| Unit | 5：射影と永続化 |
| Bolt | 5-1 第 8 章 / 5-2 第 9 章 |
| 実施日 | 2026-11-28 |
| 状態 | **執筆と実装は済み。完了条件は未達。** 第 8〜9 章の人の全文確認、main への取り込み、CI が残っている |

計画は [Unit 5 の Bolt 計画](iteration_plan-typescript-fp-5.md) にあります。

## 運用

利用者の指示「執筆完了まで、判断はすべて推奨を採用する」で進めました。

- Unit 5 の計画と、ゲート ⑤・⑥ は、同期停止していません
- `pg` と `@types/pg` の追加は、**一度、自動権限判定で拒否されました。** 作業を止めて報告し、利用者の許可を受けてから追加しました
- 章は `draft` で、main に取り込んでいません

## 成果

- [第 8 章](../article/zettai/typescript-fp/chapter08.md): 一覧の射影、command と query の分離、`GET /todo/{user}`
- [第 9 章](../article/zettai/typescript-fp/chapter09.md): PostgreSQL への追記と読み戻し、接続とトランザクションの後始末、3 経路目、モナド則
- ADR-062・063、step `zettai-step6-projection`・`zettai-step7-persistence`

追加したライブラリは `pg` 8.23.1 と `@types/pg` 8.23.1 です。データベースは、テストと開発機の使い捨てのコンテナでだけ使いました。

## 品質結果

| ゲート | 結果 | 対象 |
| :--- | :--- | :--- |
| `npm run check`（開発機の Windows、Node 22.23.4） | 130 ファイル 993 件と smoke が成功。約 40 秒 | 第 9 章を含む作業ツリー |
| 読者の手順どおりの起動（PostgreSQL に接続） | 保存した行が再起動の後も表示され、表の削除で理由の出ない 500 | 同上 |
| GitHub Actions | **実行していない** | — |
| 開発成果物レビュー | **実施していない** | — |

## 仮説の確認

> 接続の寿命を `Scope` と `Layer` で表せる。

**`Scope` で表せました。** `Layer` でも取得と解放ができることは小さなテストで確かめましたが、本体では使っていません。依存を引数で渡す決定（ADR-058）を保ちました。

## 壊して分かったこと

| 壊し方 | 結果 |
| :--- | :--- |
| 読み出しの `ORDER BY` を消す | **すべてのテストが通る** |
| いつも `COMMIT` する | 中断のテストだけが失敗する |
| 第 5 章からの生成器で射影を比べる | 500 列のうち、完了を含む列が 0 件 |
| ロックで待つ追記に 100 ミリ秒のタイムアウト | ロックの解放まで戻らない |
| 対になっていないサロゲートを `text` に保存 | エラーにならず、別の値になる |

## 確かめていないこと

| # | 確かめていないこと |
| :--- | :--- |
| 1 | CI（Linux、Node 24）での第 8〜9 章の `check`。**CI で Testcontainers の結合テストが通ること**（v0.2.0 の条件） |
| 2 | 手書きの行の読み取りや、`postgres`・`@effect/sql-pg` との比較 |
| 3 | 同時の追記、接続の切断、接続の数の上限、性能 |
| 4 | Nix のコンテナでの `check` |

## 次の Unit への持ち越し

- 読み取りから保存までを、1 つのトランザクションにする（第 10 章）
- 項目の説明の文字の制約（第 11 章）
- defect の記録（第 12 章）
- 問い合わせの時間の上限
