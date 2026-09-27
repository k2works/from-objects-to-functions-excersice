---
type: ADR
title: "ADR-007 モジュールを Unit の境界で切る"
description: "Zettai 連載 Kotlin 版で、Gradle のモジュールを原著の 7 段階ではなく本連載の Unit の境界（2 章ごと）で切る決定。原著の段階と章の区切りが一致しない事実、読者が参照する単位、命名規約を記録する。"
tags: [adr, zettai, kotlin]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-27T01:48:33Z }
---

# ADR-007 モジュールを Unit の境界で切る

日付: 2026-09-27

## ステータス

2026-09-27 提案されました

## コンテキスト

[バックエンドアーキテクチャ設計](../design/architecture_backend.md) のモジュール表は、原著のコンパニオンコードの 7 段階（`zettai_step1_http` 〜 `zettai_step7_monitoring`）に対応させる前提で書いていました。

Unit 4 の完了時点で、実態と乖離していました。

| 計画時の想定 | 実態 |
| :--- | :--- |
| `zettai-step2-domain` は第 4〜5 章 | 第 4〜7 章を含む |
| `zettai-step3-events` は第 5〜6 章 | **作られなかった** |
| `zettai-step4-projections` は第 7〜8 章 | 未作成 |

原因は [執筆計画](../article/outline.md) に書いてあったとおりです。

> 原著のコンパニオンコードは `zettai_step1_http` から `zettai_step7_monitoring` までの 7 段階しかなく、13 章と 1 対 1 に対応しません。

**原著の段階に合わせようとしたことが誤りでした。** 段階の区切りは原著の都合で、本連載の章の区切りとは違います。

## 決定

**モジュールは Unit の境界（= 2 章）で切ります。** 名前は内容を表すものにし、原著の段階番号には合わせません。

| モジュール | 章 | Unit |
| :--- | :--- | :--- |
| `zettai-step1-http` | 1〜3 | Unit 1〜2 |
| `zettai-step2-domain` | 4〜7 | Unit 3〜4 |
| `zettai-step3-persistence` | 8〜9 | Unit 5 |
| `zettai-step4-*`（予定） | 10〜11 | Unit 6 |
| `zettai-step5-*`（予定） | 12〜13 | Unit 7 |

### 理由

**読者が「その Unit の終わりの状態」を丸ごと参照できることを優先します。**

| 切り方 | 評価 |
| :--- | :--- |
| 章ごと（13 モジュール） | 細かすぎる。章ごとの差分は記事が示すので、モジュールで持つ必要がない |
| Unit ごと（7 モジュール） | **採用。** 1 つの Unit が「動く状態」の単位なので、参照する単位と一致する |
| 全部 1 つ | 過去の状態が残らない。第 3 章時点のコードを読者が見られない |

`step1`・`step2` という接頭辞は残します。順序が名前で分かるためです。ただし**番号は原著の段階ではなく本連載の通番**です。この対応関係を記事側で示します。

## 影響

- `zettai-step3-events` と `zettai-step4-projections` は作られません。アーキテクチャ設計のモジュール表を実態に合わせます
- 記事では「この章のコードは `zettai-stepN-*` にあります」と示します。原著の段階名と混同しないよう、対応表を [執筆計画](../article/outline.md) に置きます
- モジュールが増えるたびに `settings.gradle.kts` と Kover の対象に 1 行ずつ足します

## コンプライアンス

- モジュール数が Unit 数以下であること
- 各モジュールが単独で `./gradlew :<module>:check` を通ること
- アーキテクチャ設計のモジュール表が実態と一致していること

## 備考

- 起案: Unit 5 のステップ 5-1.2（[Unit 5 の Bolt 計画](../development/iteration_plan-5.md)）
- 関連: [執筆計画](../article/outline.md)、[バックエンドアーキテクチャ設計](../design/architecture_backend.md)
