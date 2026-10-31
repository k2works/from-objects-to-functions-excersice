---
type: Review
title: "Kotlin FP 版 Unit 6 開発成果物レビュー"
description: "Kotlin FP 版 Unit 6（第 10〜11 章、zettai-step6-transaction-validation）を 5 観点（プログラマー、テスター、アーキテクト、テクニカルライター、ユーザー代表）で並列レビューした結果と、指摘ごとの対応方針。"
tags: [review, zettai, kotlin, unit-6]
status: draft
generated: { by: anthropic/claude-sonnet-5-5, at: 2026-10-31T11:47:36Z }
---

# Kotlin FP 版 Unit 6 開発成果物レビュー

## 対象

- 実装: `apps/kotlin-fp/zettai/zettai-step6-transaction-validation`（コミット `2aa25bf`、`48ed75d`、`e64f158`、`2d3ad55`、`f9d8dcf`）
- 記事: `docs/article/zettai/kotlin-fp/chapter10.md`、`chapter11.md`
- 設計: ADR-042、ADR-043、`architecture_backend.md`、`domain-model.md`、`data-model.md`

5 つのエージェントを並列に起動し、各報告の見出しから重要度を数えました（高 8 / 中 約 18 / 低 約 18。低は重複を含みます）。

## 総合評価

transaction の境界を 1 か所に集め、`Tx` を型引数で通す設計と、蓄積する検証は、おおむね意図どおりに実装されています。契約テストを in-memory と PostgreSQL で共有している点、競合を待ち合わせで再現している点も評価されました。

一方、レビューは次の 3 種類の実際の問題を見つけました。

- 構造: `errors` と `validation` の間の循環 import。`BoundaryTest` は domain の外への依存しか見ないため、検出できませんでした。
- 利用者の見え方: 業務のルールで断られた理由（変名先がある、元が無い、競合）が、英語の内部の文言のままで、フォームも消えていました。
- 検証の穴: 競合テストの待ち合わせにタイムアウトが無いこと、名前変更の並行が未テストであること、受入条件の文言（「同じ結果」）がテストの実態と食い違うこと、記事の断定（経緯、型の保護の範囲）が裏付けられないこと。

## 対応方針（高）

| # | 指摘 | 指摘元 | 方針 | 対応 |
| :--- | :--- | :--- | :--- | :--- |
| 1 | `errors` ↔ `validation` の循環 import | アーキテクト | 修正 | `FieldProblem`、`InputField`、`InputProblem` を `errors` に移し、`InvalidInput` は `errors` に置いた。`BoundaryTest` に「`domain.errors` は `validation`、`commands` に依存しない」を追加 |
| 2 | 業務のルール違反が英語のプレーンテキスト | ユーザー代表 | 修正 | `describeError` で日本語の理由にし、フォームに入力を残して再表示。Cucumber の期待も日本語に |
| 3 | 409 の案内が無い | ユーザー代表 | 修正 | 「別の操作で変更されました。開き直してから、もう一度お試しください」を同じ画面に出す。`競合は 409 で、開き直す案内を同じ画面に出す` |
| 4 | 待ち合わせにタイムアウトが無い | テスター | 修正 | `withTimeout(GATE_TIMEOUT_MS)` を追加 |
| 5 | 名前変更の並行が未テスト | テスター | 修正 | `append` の直前で待ち合わせる `bothReachAppendTogether` を足し、2 件を追加（下記） |
| 6 | 受入条件「同じ結果」が実態と食い違う | テスター | 文言を修正 | 計画の表を「event が重複せず、更新が失われない点が同じ」に改めた |
| 7 | 第 10 章「4 件とも失敗」を裏付けられない | テクニカルライター | 弱めた | 観察した事実（2 つとも成功した）と、当時のテストが期待していたことに書き分けた |
| 8 | 第 11 章「テストは先に失敗」の裏付け | テクニカルライター | 維持 | このセッションで失敗を観察した（本文の言い方を「直す前に失敗しました」に） |

名前変更の並行テスト:

- `PostgreSQL: 名前変更と、変名先と同じ名前の CreateToDoList が交差すると、片方だけが成功し、項目は 1 か所にしか残らない`
- `PostgreSQL: 同じリストを別の名前へ同時に変えると、片方だけが成功し、項目は複製されない`

## 対応方針（中）

| 指摘 | 指摘元 | 方針 |
| :--- | :--- | :--- |
| 境界値（40 文字ちょうど、空白だけ、制御文字、複数の禁止文字） | テスター | 修正。`ValidationTest` に 4 件追加 |
| POST の form に `newName` が無い | テスター | 修正。400 でテスト |
| POST 経路の 409 | テスター | 修正。`append` が `ConcurrentModification` を返す store で 409 の画面をテスト |
| `ConcurrentModification` が持つ stream の数（ADR は「組」） | アーキテクト | ADR-043 の文言を修正（PostgreSQL は最初に検出した 1 つ） |
| 名前変更の落とし穴（履歴、並び順、名前の再利用） | アーキテクト | `domain-model.md` に記載 |
| `Tx` の保護範囲の断定 | アーキテクト、テクニカルライター | ADR-042 と第 10 章を弱めた（Hub と domain に対してだけ成り立つ） |
| 第 11 章の `zipAll` の引数の数、見出しの「２つ」 | テクニカルライター | 3 引数版と見出しの注記を追記 |
| `showList` が `html()` を使わない | プログラマー | 修正 |
| `Transaction<Tx>` の戻り値の固定 | プログラマー、アーキテクト | ADR-042 に理由を記載。必要になった時点で interface にする |
| 「HTTP × PostgreSQL」の組み合わせが未検証 | テスター | 第 11 章に、確かめていない組み合わせとして記載 |
| `version` を `MAX(version)` から導く | アーキテクト | **持ち越し**。ADR-043 に見直しの条件を記載 |
| `createSchema` が `version` 列の無い既存テーブルに黙って成功する | アーキテクト | **持ち越し**。ADR-043 に前提（DB を作り直す）を記載 |
| PostgreSQL の INSERT の途中の例外（`23505` 以外）の rollback テスト | テスター | **持ち越し** |
| 失敗応答（404、500 など）の画面設計は HTML、実装はプレーンテキスト | ユーザー代表 | **持ち越し**。名前変更のフォームだけを HTML にした |
| 404 の導線、成功の知らせ、`required` / `maxlength` | ユーザー代表 | **持ち越し**（低〜中） |
| `Request.listKey()` の重複 | プログラマー | **持ち越し**（低） |

## 残件

高の残件は 0 です。中の持ち越しは上の 5 件（`MAX(version)`、`createSchema` の列確認、途中例外の rollback テスト、失敗応答の HTML 化、404 の導線）です。計画の DoD「高・中の残件がない」は満たしていません。持ち越しは Unit 7 の計画で扱うか、人が許容を判断します。

## 再レビュー

修正後の再レビュー（独立した読み取り専用の確認）は実施していません。修正は、`gradlew.bat clean check --no-daemon`（全 module）と記事検査の green で確認しました。再レビューを求める場合は、この文書を根拠に起動します。
