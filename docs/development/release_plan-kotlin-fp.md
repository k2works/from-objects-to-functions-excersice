---
type: Release Plan
title: "リリース計画 - Zettai 連載（Kotlin FP 版）"
description: "Kotlin、Arrow、kotlinx.coroutines を使う Zettai Kotlin FP 版を、前提整備と全 13 章の 7 Unit で公開する AI-DLC Level 1 計画。"
tags: [development, plan, ai-dlc, zettai, kotlin, arrow]
status: draft
generated: { by: openai-codex/gpt-5, at: 2026-10-30T00:00:00Z }
---

# リリース計画 - Zettai 連載（Kotlin FP 版）

## Intent

既存 Kotlin 版と同じ Zettai・同じ 13 章を、Kotlin の標準機能、Arrow、kotlinx.coroutines で実装し、自前の関数型抽象とライブラリへ委ねる設計の違いを、読者が手元で再現できるコードとして公開します。

## 成功条件

- `apps/kotlin-fp/zettai/` の `./gradlew check` で単体・性質・Cucumber・Testcontainers のテストが通る
- `docs/article/zettai/kotlin-fp/` に索引と全 13 章があり、コード例が実装と一致する
- JDK 25、Gradle Wrapper、依存バージョンが再現可能に固定される
- 既存 Kotlin 版との選択差を各章で説明する

## Unit 分解

| Unit | Bolt | 対象 | 完了ゲート | 状態 |
| :--- | :--- | :--- | :--- | :--- |
| 1 | 1-1 / 1-2 | 前提整備 / 第 1 章 | 最小構成の `check` と CI、記事形式 | **完了** |
| 2 | 2-1 / 2-2 | 第 2〜3 章 | HTTP の縦串と Cucumber の 2 経路 | 未着手 |
| 3 | 3-1 / 3-2 | 第 4〜5 章 | 関数型 DI とイベント畳み込み | 未着手 |
| 4 | 4-1 / 4-2 | 第 6〜7 章 | 状態遷移と Arrow typed error | 未着手 |
| 5 | 5-1 / 5-2 | 第 8〜9 章 | CQRS と PostgreSQL 結合テスト | 未着手 |
| 6 | 6-1 / 6-2 | 第 10〜11 章 | transaction とエラー蓄積 | 未着手 |
| 7 | 7-1 / 7-2 | 第 12〜13 章 | 監視、横断比較、全品質ゲート | 未着手 |

依存関係は `Unit 1 → 2 → 3 → 4 → 5 → 6 → 7` です。章の物語が設計の到達順序を固定するため、Unit は直列に進めます。

## エントロピーとリスク

| 軸 | 評価 | 対応 |
| :--- | :--- | :--- |
| 意図の曖昧さ | LOW | 既存 Kotlin 版の章構成を維持する |
| 構造的不確実性 | MED | Unit 1 でライブラリ共存と境界をスパイクする |
| 検証の不確実性 | LOW | `check`、記事検査、MkDocs を機械化する |
| リスク | MED | JDK 25、effect、DB の互換性を実物で検証する |
| 未解決の仮定 | MED | 各 Unit の冒頭で公式資料と依存解決を確認する |

主なリスクは、ライブラリ API の変化、`suspend` の境界漏れ、Cucumber の仕様と adapter の混同、Testcontainers の実行環境差です。バージョンカタログ、functional core / effectful shell、同一 feature の複数経路、動的な container 接続情報で抑えます。

## 品質と進捗

- TDD は Red → Green → Refactor を守ります
- 実装と記事は別の変更単位にします
- ローカルと CI は `./gradlew check` を同じ判定に使います
- GitHub Project / Issue / Milestone には同期しません
- 完了 Unit 数、変更依頼数、リードタイムを Unit ごとに記録します

## 関連ドキュメント

- [執筆計画](../article/outline.md)
- [開発戦略](development_strategy.md)
- [Unit 1 の Bolt 計画](iteration_plan-kotlin-fp-1.md)
