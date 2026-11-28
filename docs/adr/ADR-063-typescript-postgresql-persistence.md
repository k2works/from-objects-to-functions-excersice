---
type: ADR
title: "ADR-063 TypeScript FP 版は pg で PostgreSQL に追記し、行は Schema と domain の生成の関数で読む"
description: "Zettai 連載 TypeScript FP 版で、pg で PostgreSQL に追記し、行を Schema と domain の生成の関数で読み、データベースの失敗を defect にする決定。利用者の事前の指示で推奨案を採用した。後に人がまとめて承認した。"
tags: [adr, typescript, zettai, effect, postgresql, persistence]
status: stable
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-11-28T07:34:53Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-28T07:34:54Z }
---

# ADR-063 TypeScript FP 版は pg で PostgreSQL に追記し、行は Schema と domain の生成の関数で読む

日付: 2026-11-28

## ステータス

2026-11-28 承認されました（実装と章の公開の後に、人がまとめて承認しました）

それまでの経緯: 2026-11-28 推奨案を採用しました（Unit 5 のゲート ⑤・⑥）。**同期停止していません。** 利用者の事前の指示「執筆完了まで、判断はすべて推奨を採用する」によるものです。`pg` と `@types/pg` の追加は、自動権限判定で一度拒否された後、利用者が許可しました。設計の中身を、人が個別に確認してはいません

## コンテキスト

第 9 章で、イベントを PostgreSQL に保存します。接続の driver、行の読み方（codec）、スキーマ、失敗の扱いを決めます。

### 先に確かめたこと

2026-11-28 に確かめました（Effect 4.1.6、pg 8.23.1、`postgres:18-alpine`）。

| 項目 | 結果 |
| :--- | :--- |
| `@effect/sql-pg` の版の要求 | 最新の 4.1.7 は `effect` ^4.1.7 を要求する。版の固定（ADR-053）を変えることになる |
| `Effect.promise` と `Effect.tryPromise` の reject | 前者は defect、後者は型付きの失敗になる |
| NUL を含む文字列 | `text` も `jsonb` も拒否する |
| 対になっていないサロゲート | `jsonb` は拒否する。**`text` は、エラーにせず別の値で保存する** |

## 決定

1. **driver は `pg` 8.23.1 を使い、SQL を直接書く。** 版を固定する
2. **スキーマは `todo_list_event` の 1 テーブルと、`(entity_id, id)` のインデックス。** 起動時に `CREATE TABLE IF NOT EXISTS` を実行する（[データモデル設計](../design/data-model.md)）
3. **行の payload は、Effect の `Schema` で形を調べ、値の意味は domain の生成の関数で調べる。** 種類ごとの読み方は `Record` に並べ、種類の足し忘れを型で止める
4. **データベースの失敗と、読めない行は defect にする。** [ADR-060](ADR-060-typescript-typed-errors-and-defects.md) の線を保つ。ドメインモデル設計の `PersistenceError` は、型付きの失敗としては足さない
5. **接続は `Effect.acquireUseRelease` で借りて返し、接続の集まりは `Effect.acquireRelease` で `Scope` の寿命にする。** `Layer` は使わない（[ADR-058](ADR-058-typescript-dependency-by-function-argument.md) を保つ）
6. **名前の生成で、NUL と対になっていないサロゲートを拒否する**

### 検討した代替案

| 案 | 採らなかった理由 |
| :--- | :--- |
| `@effect/sql-pg` | `effect` の版を上げることになる。この版では試していない |
| `postgres`（4.1.1） | 試していない。`pg` を選んだ根拠は、比較の結果ではない |
| 行の読み取りを手書きにする | **手書きの実装を作って比べてはいない。** 執筆計画の第一候補の `Schema` を採った |
| データベースの失敗を型付きの失敗にする | 呼び出し側に、型で分けて対応する処理（再試行など）がまだ無い |
| 接続の集まりを `Layer` で組み立てる | `Layer` でも取得と解放ができることは確かめた。依存は引数で渡しているので、`Layer` にする利点が無かった |

## 影響

### ポジティブ

- ハブ、web、核は変わらない。保管を差し替えるだけで済んだ
- メモリと PostgreSQL に、同じ契約のテストを当てられる

### ネガティブ

- データベースの失敗は、型に現れない。500 の原因は、まだ記録していない
- **中断は、実行中の問い合わせの終了を待つ。** 表がロックされた状態で 100 ミリ秒のタイムアウトを掛けると、戻ったのはロックの解放の後だった。問い合わせの時間を区切るには、データベースの側の設定が要る（この版は設定していない）
- **`ORDER BY id` を消しても、テストはすべて通った。** 追記順の保証は、SQL を読んで守る
- 項目の説明には、文字の制約が無い。保存できない文字を含むコマンドは、保存で defect になる
- 読めない行が 1 つあると、そのリストと、その利用者の一覧は 500 になる

## コンプライアンス

- 保管の契約のテストを、メモリと PostgreSQL の両方で実行している
- 1 回の保存の原子性、中断、接続の返却、壊れた行、500 の本文を確かめるテストがある
- 同じ受け入れシナリオを、DB を使う経路でも実行している

## 備考

- 著者: anthropic/claude-sonnet-5-5
- 関連 ADR: [ADR-053](ADR-053-typescript-effect-version-and-stability.md)、[ADR-060](ADR-060-typescript-typed-errors-and-defects.md)
