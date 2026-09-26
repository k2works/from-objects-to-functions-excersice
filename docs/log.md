# Docs Update Log

## 2026-09-26
* **Verification**: [iteration_plan-2](/development/iteration_plan-2.md) を human:kakimomokuri が検証
* **Creation**: [Unit 2 の Bolt 計画](/development/iteration_plan-2.md) を作成（AI-DLC 準拠）。ウォーキングスケルトンとドメイン分離を対象に、Unit 1 からの持ち込み 8 項目の消化先、5 軸のエントロピー評価、Bolt 2-1 / 2-2 のステップ計画 23 件・承認ゲート 11 箇所、ドメインモデル図と画面遷移図、Deployment Unit の完了条件を定義（claude-code/claude-opus-5）
* **Update**: [開発戦略](/development/development_strategy.md) に Unit 1 の完了と Unit 2 の計画確定を反映し、Unit 1 で積み残した静的解析の判断を Unit 2 へ移した（claude-code/claude-opus-5）
* **Creation**: [Unit 1 の Bolt 終了報告](/development/bolt_report-1.md) を作成。Bolt 1-1 / 1-2 の成果、完了 Unit 数 1・承認ゲート通過数 8・変更依頼数 1・リードタイム 1 日の実績、エントロピー評価の的中度、Unit 2 のゲート密度の決定を記録（claude-code/claude-opus-5）
* **Creation**: [開発ジャーナル 2026-09-26](/journal/20260926.md) を作成。AI-DLC への計画の作り直し、スパイクの効果、BDD 不採用の判断、記事のコード例の出所管理という学びを記録（claude-code/claude-opus-5）
* **Creation**: [第 1 章 新しいアプリケーションを準備する](/article/zettai/kotlin/chapter01.md) を公開。題材の定義、テストに開発をガイドさせる前提、Nix devShell と Gradle マルチプロジェクトのセットアップ、ボウリングの得点計算を題材にしたユニットテストの関数型化を TDD で記述（claude-code/claude-opus-5）
* **Creation**: [ADR-001 サンプル実装に Kotlin 2.2 / JDK 21 を採用する](/adr/ADR-001-kotlin-toolchain.md) を作成。原著の Kotlin 1.8.20 / JDK 11 を踏襲せず、ビルドの正を Gradle 側に置く判断と、devShell の kotlinc 2.3 との差を許容する理由を記録（claude-code/claude-opus-5）
* **Update**: Unit 1 の完了にあわせて[シリーズ索引](/article/zettai/index.md)・[Kotlin 版トップ](/article/zettai/kotlin/index.md)・[執筆計画](/article/outline.md)の進捗と前提整備チェックリストを更新（claude-code/claude-opus-5）
* **Verification**: [iteration_plan-1](/development/iteration_plan-1.md) を human:kakimomokuri が検証
* **Update**: [Unit 1 の Bolt 計画](/development/iteration_plan-1.md) を開始準備の検証結果で更新。テンプレート必須節（ゴール・リスクと対策）を追加し、局面とアプローチ（序盤アウトサイドインの適用例外）、受入条件とステップの対応表、省略する設計 4 図とマニュアルの理由、`docs/design/` への反映注記を追記（claude-code/claude-opus-5）
* **Creation**: [開発戦略](/development/development_strategy.md) を作成。7 Unit を序盤・中盤・終盤の 3 局面に割り当て、アプローチ選択の根拠、共通の TDD サイクル、承認ゲートとゲート密度、デモ項目を受け入れ基準とする方針（BDD 不採用・DDT / Pesticide 採用）、設計ドキュメント整合、局面移行時の一貫性維持を定義（claude-code/claude-opus-5）
* **Creation**: [Unit 1 の Bolt 計画](/development/iteration_plan-1.md) を作成（AI-DLC 準拠）。Unit 1 の満足条件・5 軸のエントロピー評価・スコープと深さとテスト戦略・Bolt 1-1 / 1-2 のステップ計画 18 件・承認ゲート 8 箇所・Deployment Unit の完了条件を定義（claude-code/claude-opus-5）
* **Update**: [リリース計画](/development/release_plan.md) を AI-DLC 準拠に改訂。Intent・ビジネス価値・Unit 分解・依存 DAG・エントロピー評価・スコープと深さとテスト戦略を追加し、ベロシティを完了 Unit 数・承認ゲート通過数・変更依頼数・リードタイムに置き換え（claude-code/claude-opus-5）
* **Creation**: [リリース計画 - Zettai 連載（Kotlin 版）](/development/release_plan.md) を作成。執筆計画の全 13 章と前提整備を 14 ストーリー・75SP に分解し、1 イテレーション 2 章の 7 イテレーション・3 フェーズ構成、ベロシティ見積もり、バッファ戦略、リスク台帳 8 件、進捗管理の枠組みを定義（claude-code/claude-opus-5）
* **Update**: [執筆計画](/article/outline.md) をリリース計画と相互対応させ、章別計画にストーリー・IT・SP 列とフェーズ区切りを追加。あわせて OKF フロントマターを付与（claude-code/claude-opus-5）
* **Update**: [章構成マインドマップ](/article/draft.md) に OKF フロントマターを付与（claude-code/claude-opus-5）
* **Update**: 原著コンパニオンコードを `docs/article/fotf/` から `references/fotf/`（git 管理外）へ移し、OKF バンドルの外に出した。関連ドキュメントのパス記述を更新（claude-code/claude-opus-5）
* **Creation**: [BDD導入ガイド](/reference/BDD導入ガイド.md) を作成（claude-code/claude-opus-5）

## 2026-09-12
* **Verification**: [AI-DLC用語集](/reference/AI-DLC用語集.md) を human:kakimomokuri が検証
* **Verification**: [コーディングとテストガイド_AI-DLC版](/reference/コーディングとテストガイド_AI-DLC版.md) を human:kakimomokuri が検証
* **Verification**: [ユースケース作成ガイド_AI-DLC版](/reference/ユースケース作成ガイド_AI-DLC版.md) を human:kakimomokuri が検証
* **Verification**: [リリース・イテレーション計画ガイド_AI-DLC版](/reference/リリース・イテレーション計画ガイド_AI-DLC版.md) を human:kakimomokuri が検証
* **Verification**: [開発ガイド_AI-DLC版](/reference/開発ガイド_AI-DLC版.md) を human:kakimomokuri が検証
* **Verification**: [AI-DLC導入ガイド](/reference/AI-DLC導入ガイド.md) を human:kakimomokuri が検証
* **Creation**: [AI-DLC 用語集](/reference/AI-DLC用語集.md) を新規作成。論文・参照実装・本プロジェクトの AI-DLC 版ガイドの用語を 6 区分で定義し、XP・Scrum との対応表と参照先を追加。`CLAUDE.md` の参照表にも登録。
* **Creation**: [開発ガイド（AI-DLC 版）](/reference/開発ガイド_AI-DLC版.md) を新規作成。XP 版の開発ライフサイクル（分析・開発・運用・構築・配置）を AI-DLC の 3 フェーズで読み替え、各活動で AI が生成し人が検証するものを定義。AI-DLC 版ガイド 4 本と XP 版ガイドの対応表を追加。あわせて `CLAUDE.md` に AI-DLC を採用する旨と守るべき 6 原則を明記し、ペルソナの参照先を開発ガイド（AI-DLC 版）に変更。
* **Creation**: [コーディングとテストガイド（AI-DLC 版）](/reference/コーディングとテストガイド_AI-DLC版.md) を新規作成。XP 版の章構成を保ちながら、TDD の三原則を AI に守らせる Bolt 開発フロー、承認ゲートの密度選択、AI 向けのアプローチ指示、ステップ計画、Red/Green/Refactor 各フェーズの人の検証観点、AI 生成コード固有の品質観点、ソース束縛レビュー、テスト戦略の水準、ガードレール化、quick-cement としての技術的負債、開発スキルの読み替え表を定義。
* **Creation**: [ユースケース作成ガイド（AI-DLC 版）](/reference/ユースケース作成ガイド_AI-DLC版.md) を新規作成。XP 版の章構成を保ちながら、Mob Elaboration によるユースケース作成手順、12 ステップの AI と人の分担、トレーサビリティ項目を加えたテンプレート、セマンティクス密度、AI 生成ユースケースの検証観点、ブラウンフィールドのリバースエンジニアリング、プロンプトパターン、分析スキルの読み替え表を定義。
* **Creation**: [リリース・イテレーション計画ガイド（AI-DLC 版）](/reference/リリース・イテレーション計画ガイド_AI-DLC版.md) を新規作成。XP 版の章構成を保ちながら、Intent → Unit → Bolt の計画階層、承認駆動の Bolt 計画、エントロピー評価による見積もり、完了 Unit 数・ゲート通過数・リードタイムによる進捗管理、リスク台帳、Bolt 終了報告テンプレート、計画スキルの読み替え表を定義。
* **Update**: [AI-DLC 導入ガイド](/reference/AI-DLC導入ガイド.md) を一次情報（Method Definition Paper・aidlc-workflows ユーザーガイド）で精査し拡充。10 の基本原則、成果物定義（Intent・Unit・Bolt・Domain/Logical Design・Deployment Unit）、Inception の 6 成果物、グリーンフィールド／ブラウンフィールド実践例、付録 A のプロンプトパターン、参照実装の 5 フェーズ 33 ステージ・スコープ・エージェント・承認ゲート・監査ログ、33 ステージと Skills の対応表を追加。
* **Creation**: [AI-DLC 導入ガイド](/reference/AI-DLC導入ガイド.md) を新規作成。AI-DLC の概要・コア原則・3 フェーズ・ベストプラクティスを整理し、XP と Skills 体系への対応表と段階的な導入ステップを定義。

## 2026-08-26
* **Verification**: [ドキュメント構成ガイド](/reference/ドキュメント構成ガイド.md) を human:kakimomokuri が検証
* **Update**: ドキュメント構成ガイドを更新。docs/review を共通からプロジェクト別カテゴリに変更（プロジェクト別は 7 カテゴリに）。
* **Creation**: ドキュメント構成ガイドを新規作成。単一企業・統合戦略・複数プロジェクトのコンセプトと apps/ との対応規約を定義。

## 2026-08-25
* **Update**: リンク切れ 53 件を修正。`grokking-concurrency` のサンプルコード参照をインラインコード表記に統一、`functional-desgin-ppp/elixir` の目次 6〜10 章を実際の章構成に合わせて書き直し、[Codex CLI MCP アプリケーション開発フロー](/reference/CodexCLIMCPアプリケーション開発フロー.md) の関連ドキュメントを実在ガイドに付け替え、未執筆の付録は「未作成」と明記。`template/まずこれを読もうリスト.md` の 10 件はコピー先基準のパスのため据え置き。
* **Migration**: `docs/` を OKF v0.2 の知識バンドルに移行。601 件のコンセプト（Article 552 件・Reference 31 件・Template 18 件）に `type`・`title`・`description`・`tags`・`generated` を付与し、ルート `index.md` に `okf_version: "0.2"` を宣言。本文は変更していない。Wiki.js 由来のフロントマターは OKF 形式に併合した。
