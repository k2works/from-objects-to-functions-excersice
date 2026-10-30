---
type: Review
title: "Kotlin FP 版 Unit 2 開発成果物レビュー"
description: "Kotlin FP 版 Unit 2 の HTTP ウォーキングスケルトン、Cucumber 2 経路、第 2〜3 章を 5 観点でレビューした結果。"
tags: [review, development, zettai, kotlin, unit-2]
status: draft
generated: { by: openai-codex/gpt-5, at: 2026-10-30T05:24:30Z }
---

# Kotlin FP 版 Unit 2 開発成果物レビュー

## レビュー対象

- `apps/kotlin-fp/zettai/zettai-step2-http-domain/`
- `docs/article/zettai/kotlin-fp/chapter02.md`
- `docs/article/zettai/kotlin-fp/chapter03.md`
- `docs/development/iteration_plan-kotlin-fp-2.md`

## 総合評価

xp-programmer、xp-tester、xp-architect、xp-technical-writer、xp-user-representative の 5 観点でレビューしました。初回レビューでは依存方向、高階関数の不在、feature への技術的経路の露出、進捗不整合などの高重要度指摘がありました。すべて修正し、再レビューと clean build で高重要度の残件がないことを確認しました。

## 改善提案と対応

### 高

| # | 提案 | 指摘元 | 対応 |
| :--- | :--- | :--- | :--- |
| 1 | 取得 port と pure lookup を `web` の内側へ移す | programmer / architect | `domain.findToDoList` と `application.ToDoListFetcher` に分離して修正 |
| 2 | `ZettaiActions` を interface ではなく関数値で表す | programmer / architect / writer | 関数プロパティを持つ `data class` と constructor function に修正 |
| 3 | Gherkin feature から `domain` / `HTTP` を除く | writer / user | step definitions が同じ操作を 2 経路へ適用する形に修正 |
| 4 | 公開章数を 3 / 13 に統一する | writer / user | シリーズ索引と進捗表を修正 |
| 5 | escape テストと HTTP 2 経路の特殊文字同値性を成立させる | tester | renderer / HTTP テストを分離し、test adapter で既知 entity を decode |
| 6 | feature の scenario 境界を直す | architect / writer | 成功 scenario と missing scenario を分離し、clean test で確認 |

### 中

| 提案 | 対応 |
| :--- | :--- |
| HTML を escape する | `& < > " '` の純粋変換とテストを追加 |
| HTTP の media type を契約に含める | `text/html; charset=utf-8` を設定しテストを追加 |
| カンマを含む項目を扱う | Cucumber DataTable へ変更 |
| missing を 2 経路で検証する | 2 本目の Gherkin scenario を追加 |
| 複数ユーザーの同名リストを区別する | pure lookup test を追加 |
| `suspend` と blocking JDBC の違いを説明する | 第 2 章に第 9 章で boundary を決める旨を追記 |
| in-process handler の検証範囲を明示する | 第 2 章に server を待受しないことと実行コマンドを追記 |

### 低・保留

- import 走査は fully-qualified reference を見逃します。現 Unit では簡潔さを優先し、将来の module 分割時にコンパイラ境界へ強化します。
- HTTP test adapter の HTML 解析は Unit 2 の固定した最小 HTML に限定します。テンプレートを導入する第 11 章で解析方法を再検討します。

## 検証結果

- `gradlew.bat clean check --no-daemon`: 成功
- Unit 2: Kotest 7 件、Cucumber 2 scenario が成功
- Unit 1: Kotest / Cucumber / Testcontainers PostgreSQL が回帰なく成功
- 記事コード・節構成・数値・ADR 参照検査: 違反 0
- OKF: ERROR 0（既存 template 警告 10）
- MkDocs strict build: 成功

## 判定

高重要度の指摘はすべて修正済みです。Unit 2 の完了条件を満たします。
