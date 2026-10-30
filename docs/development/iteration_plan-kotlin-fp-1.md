---
type: Bolt Plan
title: "Unit 1 の Bolt 計画 - Zettai 連載（Kotlin FP 版）"
description: "Kotlin FP 版 Unit 1 の JDK 25 基盤、Arrow・Cucumber・Testcontainers の互換性検証、第 1 章執筆を定義する Bolt 計画。"
tags: [development, plan, ai-dlc, bolt, zettai, kotlin, arrow, unit-1]
status: stable
generated: { by: openai-codex/gpt-5, at: 2026-10-30T00:00:00Z }
---

# Unit 1 の Bolt 計画 - Zettai 連載（Kotlin FP 版）

## 概要

| 項目 | 内容 |
| :--- | :--- |
| Unit | 1：実行環境と第 1 章 |
| Bolt | 1-1 前提整備 / 1-2 第 1 章 |
| アプローチ | 基盤はスパイク、第 1 章の実装はインサイドアウト TDD |
| テスト戦略 | Standard。単体、Cucumber、PostgreSQL 結合テストを含む |

## ゴールと受入条件

- [x] Gradle JVM toolchain と CI が JDK 25 に固定されている
- [x] Kotlin 2.4.21 / Gradle 9.7.0 / Arrow 2.2.3 / Kotest 6.2.5 / Cucumber 8.0.4 が共存する
- [x] 同じ Gherkin scenario が domain / HTTP の 2 経路で通る
- [x] Testcontainers PostgreSQL が動的な接続情報で通る
- [x] 純粋関数、`Either`、`suspend` のテストを Red → Green で通す
- [x] `./gradlew check` が green になる
- [x] 第 1 章の節構成が章構成マインドマップと一致する
- [x] MkDocs の nav とシリーズ索引から第 1 章へ到達できる

Windows / WSL に Nix がないため Docker 上の Nix で同じ flake を評価し、`nix develop path:/workspace#kotlin-fp --command java -version` が JDK 25.0.1 を示すことを確認しました。

## Bolt 1-1: 前提整備

- [x] Gradle マルチプロジェクトと version catalog を作る
- [x] JDK 25 で動く Gradle Wrapper 9.7.0 を作る
- [x] Nix devShell `kotlin-fp` を登録する
- [x] Kotest の失敗するテストを実行して Red を確認する
- [x] pure / `Either` / `suspend` / HTTP boundary の最小実装で Green にする
- [x] Cucumber の feature と step definitions を作る
- [x] Testcontainers PostgreSQL のスモークテストを作る
- [x] 固定 PostgreSQL service を持たない CI を作る
- [x] 記事検査を `kotlin-fp` とハイフン付きパスへ対応させる

## Bolt 1-2: 第 1 章

- [x] 既存 Kotlin 版と Grokking FP Kotlin 版の純粋関数の説明を照合する
- [x] 純粋関数、参照透過性、式本体関数、`val` を Zettai の導入へ接続する
- [x] 動作確認済みコードだけを記事へ転記する
- [x] 対象索引、シリーズ索引、MkDocs nav を更新する
- [x] ローカル PlantUML server を使い MkDocs strict build を完走させ、CI に同じ service を追加する

## リスクと対策

| リスク | 対策 |
| :--- | :--- |
| JDK 25 で Gradle が動かない | JDK 25 実行対応の Gradle 9.7.0 を Wrapper で固定 |
| Cucumber と Kotest の engine が競合する | JUnit Platform 上で同一 `test` task を実行 |
| DB テストが固定環境に依存する | Testcontainers の JDBC URL と資格情報だけを利用 |
| effect が core へ漏れる | `runBlocking` を同期 HTTP adapter の 1 箇所に限定 |

## 完了条件

- [x] `gradlew.bat check --no-daemon` が JDK 25 と Docker 上で green
- [x] 記事コード、節構成、数値、ADR 参照の検査が green
- [x] `mkdocs build --strict` が green
- [x] 変更差分にビルド成果物と認証情報が含まれない

## 関連ドキュメント

- [リリース計画](release_plan-kotlin-fp.md)
- [執筆計画](../article/outline.md)
- [第 1 章](../article/zettai/kotlin-fp/chapter01.md)
