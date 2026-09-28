# 開発

開発フェーズのドキュメントです。リリース計画、イテレーション計画、ふりかえり、完了報告書を管理します。

## ドキュメント一覧

### リリース計画

| ドキュメント | 説明 |
|-------------|------|
| [リリース計画（Kotlin 版）](release_plan.md) | AI-DLC の Level 1 計画。Intent、Unit 分解（7 Unit）、依存 DAG、エントロピー評価、スコープ／検証バッファ、リスク台帳、進捗指標 |
| [リリース計画（なでしこ3 版）](release_plan-nadesiko.md) | シリーズ 2 言語目の Level 1 計画。cnako3 の採用根拠、7 Unit・14 ストーリーへの分解、型・テストフレームワーク・DB プラグインの不在に対するリスク台帳、契約テストを加えた承認ゲート方針 |
| [開発戦略](development_strategy.md) | 局面別 TDD アプローチ（序盤・終盤アウトサイドイン／中盤インサイドアウト）、承認ゲートとゲート密度、デモ項目を受け入れ基準とする方針 |

### Bolt 計画（イテレーション計画）

| Unit | Bolt 計画 | ふりかえり | Bolt 終了報告 | 状態 |
|---------------|------|-----------|-----------|------|
| Unit 1 実行環境と第 1 章 | [Unit 1 の Bolt 計画](iteration_plan-1.md) | - | [Bolt 終了報告](bolt_report-1.md) | **完了** |
| Unit 2 ウォーキングスケルトンとドメイン分離 | [Unit 2 の Bolt 計画](iteration_plan-2.md) | - | [Bolt 終了報告](bolt_report-2.md) | **完了** |
| Unit 3 関数型 DI とイベント | [Unit 3 の Bolt 計画](iteration_plan-3.md) | - | [Bolt 終了報告](bolt_report-3.md) | **完了** |
| Unit 4 コマンドとエラーハンドリング | [Unit 4 の Bolt 計画](iteration_plan-4.md) | - | [Bolt 終了報告](bolt_report-4.md) | **完了** |
| Unit 5 射影と永続化 | [Unit 5 の Bolt 計画](iteration_plan-5.md) | - | [Bolt 終了報告](bolt_report-5.md) | **完了** |
| Unit 6 文脈の受け渡しとバリデーション | [Unit 6 の Bolt 計画](iteration_plan-6.md) | - | [Bolt 終了報告](bolt_report-6.md) | **完了** |
| Unit 7 監視とアーキテクチャ総括 | [Unit 7 の Bolt 計画](iteration_plan-7.md) | [ふりかえり](retrospective-7.md) | [Bolt 終了報告](bolt_report-7.md) | **完了** |

### Bolt 計画（なでしこ3 版）

| Unit | Bolt 計画 | ふりかえり | Bolt 終了報告 | 状態 |
|---------------|------|-----------|-----------|------|
| Unit 1 実行環境・テスト基盤と第 1 章 | [Unit 1 の Bolt 計画](iteration_plan-nadesiko-1.md) | [ふりかえり](retrospective-nadesiko-1.md) | [Bolt 終了報告](bolt_report-nadesiko-1.md) | **完了** |
| Unit 2 ウォーキングスケルトンとドメイン分離 | [Unit 2 の Bolt 計画](iteration_plan-nadesiko-2.md) | [ふりかえり](retrospective-nadesiko-2.md) | [Bolt 終了報告](bolt_report-nadesiko-2.md) | **完了** |
| Unit 3 関数型 DI とイベント | [Unit 3 の Bolt 計画](iteration_plan-nadesiko-3.md) | [ふりかえり](retrospective-nadesiko-3.md) | [Bolt 終了報告](bolt_report-nadesiko-3.md) | **完了** |
| Unit 4 コマンドとエラーハンドリング | [Unit 4 の Bolt 計画](iteration_plan-nadesiko-4.md) | [ふりかえり](retrospective-nadesiko-4.md) | [Bolt 終了報告](bolt_report-nadesiko-4.md) | **完了** |
| Unit 5 射影と永続化 | [Unit 5 の Bolt 計画](iteration_plan-nadesiko-5.md) | [ふりかえり](retrospective-nadesiko-5.md) | [Bolt 終了報告](bolt_report-nadesiko-5.md) | **完了** |
| Unit 6 文脈の受け渡しとバリデーション | [Unit 6 の Bolt 計画](iteration_plan-nadesiko-6.md) | - | - | 計画済み |

Unit（イテレーション）開始時に行を追加します。AI-DLC では 1 Unit = 2 Bolt（章 1 本ずつ）とし、Bolt のステップ計画と承認ゲートを各計画に書きます。

### 進捗サマリー

AI-DLC ではベロシティの代わりに完了 Unit 数・承認ゲート通過数・変更依頼数・リードタイムで測ります。

| Unit | 計画検証負荷 | ゲート通過数 | 変更依頼数 | リードタイム |
|---------------|---------|---------|--------|--------|
| Unit 1 | 8 | 8 / 8 | 1 | 1 日 |
| Unit 2 | 13 | 11 / 11 | 1 | 1 日 |
| Unit 3 | 13 | 5 / 5 | 0 | 1 日 |
| Unit 4 | 10 | 4 / 4 | 0 | 1 日 |
| Unit 5 | 13 | 8 / 8 | 0 | 1 日 |
| Unit 6 | 10 | 6 / 6 | 0 | 1 日 |
| Unit 7 | 8 | 5 / 5 | 0 | 1 日 |
| **累計** | **75 / 75** | **47** | **2** | 完了 Unit 数 **7 / 7** |

### フェーズ進捗

| フェーズ | 内容 | Unit | 検証負荷 | 完了 Unit | 状態 |
|---------|------|-----|---------|------|------|
| Phase 1 | 土台とウォーキングスケルトン（前提整備・第 1〜3 章） | Unit 1-2 | 21 | 2 / 2 | **完了** |
| Phase 2 | ドメインをイベントと関数で表す（第 4〜9 章） | Unit 3-5 | 36 | 3 / 3 | **完了**（Release v0.2.0） |
| Phase 3 | モナドから関数型アーキテクチャへ（第 10〜13 章） | Unit 6-7 | 18 | 2 / 2 | **完了**（Release v1.0.0） |

### リリース完了報告書

| リリース | 報告書 | 状態 |
|---------|--------|------|
| Release v1.0.0（全 13 章公開） | [リリース完了報告](release_report-1.0.0.md) | **完了** |

## 補足

- **全 7 Unit が完了し、Release v1.0.0（全 13 章公開）に到達しました。** 実績は [リリース完了報告](release_report-1.0.0.md) にまとめています。
- 本プロジェクトは AI-DLC で開発します。計画の用語と進め方は [リリース・イテレーション計画ガイド（AI-DLC 版）](../reference/リリース・イテレーション計画ガイド_AI-DLC版.md) を正とします。
- テンプレートは [template/リリース計画.md](../template/リリース計画.md)、[template/イテレーション計画.md](../template/イテレーション計画.md)、[template/イテレーション完了報告書.md](../template/イテレーション完了報告書.md)、[template/リリース完了報告書.md](../template/リリース完了報告書.md) を利用できます。
