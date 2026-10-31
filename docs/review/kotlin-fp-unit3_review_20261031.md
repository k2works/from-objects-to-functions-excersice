---
type: Review
title: "Kotlin FP 版 Unit 3 開発成果物レビュー"
description: "Kotlin FP 版 Unit 3 の関数型 DI、イベント畳み込み、第 4〜5 章を 5 観点でレビューした結果。"
tags: [review, development, zettai, kotlin, unit-3]
status: draft
generated: { by: openai-codex/gpt-5, at: 2026-10-31T00:15:57Z }
---

# Kotlin FP 版 Unit 3 開発成果物レビュー

## レビュー対象

- `apps/kotlin-fp/zettai/zettai-step3-domain-events/`
- `docs/article/zettai/kotlin-fp/chapter04.md`
- `docs/article/zettai/kotlin-fp/chapter05.md`
- `docs/development/iteration_plan-kotlin-fp-3.md`

## 総合評価

xp-programmer、xp-tester、xp-architect、xp-technical-writer、xp-user-representative の 5 観点でレビューしました。初回は高 4 件、中 14 件、低 7 件でした。重複する指摘を統合して実装、テスト、記事、計画を修正し、再レビューで高・中の残件がないことを確認しました。

## 主な改善と対応

| 重要度 | 改善 | 対応 |
| :--- | :--- | :--- |
| 高 | package 境界テストが対象 source 不在でも成功する | module と production source の存在を検証してから import を走査する形へ修正 |
| 高 | Unit 2 の画面を変えない計画と renderer の変更が不一致 | status / due date の表示変更を戻し、HTTP 契約を Unit 2 と同じに維持 |
| 高 | 公開章数が重複し、進捗が 3 / 13 と表示される | Kotlin FP 版を 5 / 13 に統一 |
| 高 | 原著者名が誤っている | Michał Płachta に修正 |
| 中 | read-only `Map` の別名参照で状態が変わり得る | `ToDoListState` の生成経路を閉じ、map と items を defensive copy |
| 中 | in-memory fetcher と application port の責務が混在する | `adapter.inmemory` へ driven adapter を分離 |
| 中 | replay の法則が固定例だけである | 結合律、左右単位元、列連結との対応を生成入力で検証 |
| 中 | property test と固定 edge case の保証範囲が曖昧 | 任意列の法則と重複作成などの固定例を記事で区別 |
| 中 | Unix と Windows の実行コマンドが混在する | shell ごとに独立した再現可能なコマンドを掲載 |
| 中 | driving / driven adapter の説明が不正確 | HTTP と in-memory の役割と依存矢印を分けて記述 |

## 低優先度・保留

- package 境界検査は fully-qualified reference を完全には検出しません。module 境界へ強化する段階で再検討します。
- `runBlocking` は同期 HTTP 境界に限定しています。実サーバーと blocking I/O を導入する Unit 5 で実行モデルを再評価します。
- HTML parser の導入はテンプレートを扱う第 11 章まで保留します。

## 検証結果

- `gradlew.bat clean check --no-daemon`: 成功
- Unit 3: Kotest 17 件が成功
- Unit 1 / 2: Testcontainers PostgreSQL、Cucumber 2 scenario を含む回帰が成功
- 記事コード・節構成・数値・引用・ADR 参照検査: 違反 0
- OKF: ERROR 0（既存 template 警告 10）
- MkDocs strict build: 成功

## 判定

高・中重要度の指摘はすべて修正済みです。Unit 3 の完了条件を満たします。
