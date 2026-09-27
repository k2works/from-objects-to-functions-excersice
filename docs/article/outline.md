---
type: Writing Plan
title: "執筆計画：関数型プログラミングで作る変更を楽に安全にできるソフトウェア"
description: "Uberto Barbini 著『From Objects to Functions』を下敷きにした Zettai 連載の執筆計画。多言語シリーズの対象一覧、記事と実装の対称ファイル構成、全 13 章と原著コンパニオンコードの対応、前提整備と実行環境の方針、リリース計画のストーリー・イテレーション・SP と対応づけた章別計画、フェーズ区切り、執筆規約を定義する。"
tags: [article, plan, zettai]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-26T12:29:40Z }
---

# 執筆計画：関数型プログラミングで作る変更を楽に安全にできるソフトウェア

本シリーズは、Uberto Barbini 著『From Objects to Functions』を下敷きに、ToDo リストアプリケーション **Zettai** を TDD で一から作りながら、オブジェクト指向から関数型へ設計を移していく連載です。

- 章・節構成の一次情報は [章構成マインドマップ](draft.md) です。記事の節見出しはこれに一致させます。
- 原著のコンパニオンコードは `references/fotf/` に読み取り専用の参照元として置いています。**記事に載せるコードは参照元からの転載ではなく、`apps/{lang}/zettai/` の自作実装から転記します。**
- シリーズの索引は [Zettai シリーズ索引](zettai/index.md) です。
- 本計画を AI-DLC の Intent・Unit・Bolt に落としたものが [リリース計画](../development/release_plan.md)（Level 1 計画）です。本ファイルは章構成と執筆規約の一次情報、リリース計画は進行と進捗の一次情報とし、章の割り当てが変わったときは両方を揃えます。
- 各章は 1 つの **Bolt**（時間〜日単位の作業単位）に対応し、2 章で 1 つの **Unit**（独立して公開できる単位）を構成します。局面別の TDD アプローチと承認ゲートの密度は [開発戦略](../development/development_strategy.md) にあります。

## 対象一覧

本シリーズは同じ 13 章構成を言語ごとに書き起こす多言語シリーズです。現在の対象は **1 言語** です。

| 対象 | 実行環境 | 章数 | 記事 | サンプル実装 | 状態 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Kotlin | Nix devShell `kotlin`（JDK 21 / Gradle） | 13 | `docs/article/zettai/kotlin/` | `apps/kotlin/zettai/` | 連載中（9 / 13 章公開） |

2 言語目を追加した時点で、横断比較コンテンツ `docs/article/zettai/comparison/` を新設します。それまでは比較コンテンツを作りません。

## ファイル構成

```text
docs/article/
├── index.md              # 記事シリーズの入口
├── outline.md            # 本ファイル（執筆計画）
├── draft.md              # 全 13 章のマインドマップ（節構成の一次情報）
├── zettai/
│   ├── index.md          # シリーズ索引・全章構成表・進捗管理表
│   └── kotlin/
│       ├── index.md      # Kotlin 版トップ
│       └── chapter01.md … chapter13.md

references/
└── fotf/                 # 原著コンパニオンコード（改変しない参照元。git 管理外）

apps/
└── kotlin/
    └── zettai/           # 自作実装（Gradle マルチプロジェクト）
        ├── settings.gradle.kts
        ├── build.gradle.kts
        ├── gradle/libs.versions.toml
        └── zettai-stepN-*/
```

記事は `docs/article/zettai/{lang}/`、実装は `apps/{lang}/zettai/` の対称構成とし、言語を追加してもこの形を崩しません。

## 章と参照元モジュールの対応

原著のコンパニオンコードは `zettai_step1_http` から `zettai_step7_monitoring` までの 7 段階しかなく、13 章と 1 対 1 に対応しません。各章の参照先と、その章を書き終えた時点で自作実装が到達しているべき状態を次のとおり定めます。

| 章 | テーマ | 参照元モジュール | 自作実装の到達点 |
| :--- | :--- | :--- | :--- |
| 1 | 新しいアプリケーションを準備する | `kotlin/`、`bowlingkata/` | プロジェクト雛形と関数型ユニットテスト 1 本 |
| 2 | 関数を使って HTTP を扱う | `zettai_step1_http` | http4k で ToDo リストを表示（ウォーキングスケルトン） |
| 3 | ドメインの定義とテスト | `zettai_step2_domain` | 高階関数と DDT / Pesticide の導入 |
| 4 | ドメインとアダプタのモデリング | `zettai_step2_domain` | 関数型の依存性注入（`ToDoListHub`、`Fetcher`） |
| 5 | イベントで状態を変更する | `zettai_step3_events` | `events/` の畳み込みとモノイド |
| 6 | コマンドを実行してイベントを生成する | `zettai_step3_events` | `commands/` と関数型ステートマシン |
| 7 | 関数型手法によるエラーハンドリング | `zettai_step4_projections` の `fp/Outcome` | `Outcome` とファンクタ |
| 8 | ファンクタを使ってイベントを射影する | `zettai_step4_projections` | `queries/` による射影と CQRS |
| 9 | モナドによる安全なデータ永続化 | `zettai_step5_persistence` | PostgreSQL 結合テストとモナド則 |
| 10 | コンテキストを読み込み、コマンドを処理する | `zettai_step5_persistence` / `zettai_step6_validation` の `fp/ContextReader` | `ContextReader` によるコマンド処理 |
| 11 | アプリカティブによるデータバリデーション | `zettai_step6_validation` | `fp/Validation` とテンプレートエンジン |
| 12 | 監視と関数型 JSON | `zettai_step7_monitoring` | `logger/`、Kondor による JSON、プロファンクタ |
| 13 | 関数型アーキテクチャの設計 | 全モジュール | アーキテクチャの総括（実装追加は最小） |

## 前提整備

章の執筆に入る前に、次を完了させます。リリース計画では冒頭 4 項目を US-000（Unit 1 / Bolt 1-1）、CI の 1 項目を US-002（Unit 2 / Bolt 2-1）に含めています。

- [x] `ops/nix/environments/kotlin/shell.nix` を新設（既存 `java/shell.nix` を雛形に `kotlin`・JDK 21・Gradle を載せる）
- [x] `flake.nix` の `devShells` に `kotlin` を登録し、`nix flake show` で認識されることを確認
- [x] `apps/kotlin/zettai/` に Gradle マルチプロジェクトの雛形を作成（`gradle/libs.versions.toml` でバージョンを集中管理）
- [x] 最小のテスト 1 本が `./gradlew check` で通ることを確認
- [x] `.github/workflows/kotlin-zettai.yml` を新設（第 2 章の実装が入った直後。Unit 2 で実施）

### 実行環境の方針

- 原著は Kotlin 1.8.20 / `jvmToolchain(11)` ですが、自作実装は **Kotlin 2.x / JDK 21** を採用します。原著と挙動や API が異なる箇所は該当章に注記します。
- 第 9 章以降で必要になる PostgreSQL は、リポジトリ既存の `docker-compose.yml` にサービスを追加する方式とします。`ops/scripts/` に独自の一時スクリプトを追加せず、起動・停止が必要なら `operating-script` スキルで正式な Gulp タスクとして追加します。
- 参照元 `references/fotf/settings.gradle` は存在しない `twowords` モジュールを include しているため、そのままではビルドできません。参照元は読むだけとし、ビルドは `apps/kotlin/zettai/` でのみ行います。

## 章別計画

各章は「実装 → 記事執筆 → サイト反映 → コミット」の順で 1 Bolt として進めます。Bolt のステップ計画と承認ゲートは各 Bolt 計画（`docs/development/iteration_plan-N.md`）に書きます。実装と記事はコミットを分けます（`feat(kotlin): ...` と `docs(kotlin): ...`）。

章と [リリース計画](../development/release_plan.md) の Unit・Bolt・ストーリーの対応は次のとおりです。検証負荷（人が検証に費やす時間の相対値）と Unit / Bolt の一次情報はリリース計画側です。

| 章 | ファイル | ストーリー | Unit | Bolt | 検証負荷 | その章の焦点 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| — | （前提整備） | US-000 | Unit 1 | 1-1 | 5 | 実行環境とサンプル実装の雛形を用意する |
| 1 | `chapter01.md` | US-001 | Unit 1 | 1-2 | 3 | Zettai の題材設定と、テストに開発をガイドさせるという前提を置く |
| 2 | `chapter02.md` | US-002 | Unit 2 | 2-1 | 8 | 関数合成で HTTP を扱い、動く最小の縦串を通す |
| 3 | `chapter03.md` | US-003 | Unit 2 | 2-2 | 5 | 受け入れテストを高階関数で抽象化し、ドメインとインフラを分離する |
| 4 | `chapter04.md` | US-004 | Unit 3 | 3-1 | 5 | 関数型の依存性注入で、アダプタを差し替え可能にする |
| 5 | `chapter05.md` | US-005 | Unit 3 | 3-2 | 8 | 状態変更をイベントの畳み込みとして表し、モノイドを見出す |
| 6 | `chapter06.md` | US-006 | Unit 4 | 4-1 | 5 | コマンドからイベントを生成する関数型ステートマシンを書く |
| 7 | `chapter07.md` | US-007 | Unit 4 | 4-2 | 5 | 例外ではなく `Outcome` で失敗を型に載せ、ファンクタを理解する |
| 8 | `chapter08.md` | US-008 | Unit 5 | 5-1 | 5 | 射影でクエリ側を分離し、CQRS に到達する |
| 9 | `chapter09.md` | US-009 | Unit 5 | 5-2 | 8 | PostgreSQL への永続化をモナドで安全に組み立てる |
| 10 | `chapter10.md` | US-010 | Unit 6 | 6-1 | 5 | `ContextReader` でトランザクション文脈をコマンド処理に渡す |
| 11 | `chapter11.md` | US-011 | Unit 6 | 6-2 | 5 | アプリカティブで複数パラメータのバリデーションを合成する |
| 12 | `chapter12.md` | US-012 | Unit 7 | 7-1 | 5 | 構造化ロギングと関数型 JSON、プロファンクタを扱う |
| 13 | `chapter13.md` | US-013 | Unit 7 | 7-2 | 3 | ここまでの設計判断を関数型アーキテクチャとして総括する |
| **合計** | | **14 ストーリー** | **7 Unit** | **14 Bolt** | **75** | |

### フェーズ区切り

| フェーズ | 章 | Unit | 検証負荷 | 焦点 | リリース |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Phase 1 土台とウォーキングスケルトン | 前提整備・1〜3 | Unit 1〜2 | 21 | 実行環境・HTTP の縦串・ドメインとインフラの分離 | v0.1.0 |
| Phase 2 ドメインをイベントと関数で表す | 4〜9 | Unit 3〜5 | 36 | 関数型 DI・イベント・コマンド・`Outcome`・射影・永続化 | v0.2.0 |
| Phase 3 モナドから関数型アーキテクチャへ | 10〜13 | Unit 6〜7 | 18 | `ContextReader`・バリデーション・監視・設計の総括 | v1.0.0 |

フェーズの区切りは [章構成マインドマップ](draft.md) のテーマの切れ目のうち、Unit の境界と一致する位置に置いています。Unit の依存はチェーン状で、並列に回せる Unit はありません（原著の章立てが設計の到達順序を固定しているため）。

## 執筆規約

- 見出しは階層を飛ばさない。章内の節は [draft.md](draft.md) のマインドマップに一致させる
- コードブロックには言語を明示する。差分を示すときは変更箇所が分かる粒度で切る
- **記事のコード例は必ず `apps/kotlin/zettai/` の動作確認済み実装から転記する。** 憶測で書いたコードを載せない
- 長い完成コードは `<details>` に入れ、本文には差分だけを載せる
- 日本語と半角英数字の間に半角スペース。文体はですます調。技術用語は英語のまま
- 読者が実行するコマンドは、プロンプト記号を混ぜず、コピーしてそのまま動く形で書く
- 原著のコードや文章をそのまま転載しない。参照した箇所は出典として示す
- 章を追加したら `mkdocs.yml` の nav、[シリーズ索引](zettai/index.md) の進捗管理表、本ファイルの対象一覧を必ず揃える
- 章の割り当て・検証負荷・Unit / Bolt の構成を変えたら、本ファイルの章別計画と [リリース計画](../development/release_plan.md) の Unit 分解・ストーリー一覧・進捗状況を同じコミットで揃える
