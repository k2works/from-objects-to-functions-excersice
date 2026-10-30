---
type: Review
title: "Kotlin FP 版 Unit 1 開発成果物レビュー"
description: "Kotlin FP 版 Unit 1 をプログラマー、テスター、アーキテクト、テクニカルライター、ユーザー代表の 5 視点でレビューした結果。"
tags: [review, development, kotlin, arrow, unit-1]
status: stable
generated: { by: openai-codex/gpt-5, at: 2026-10-30T00:00:00Z }
---

# Kotlin FP 版 Unit 1 開発成果物レビュー

## 総合評価

JDK 25、Gradle、Kotest、Cucumber、Testcontainers の最小構成は成立しました。レビューで見つかった production へのテスト経路漏れ、HTTP request を読まない偽陽性、依存の全 module 配布、effect 呼び出し回数の未検証、完了表示の不整合は修正済みです。

レビュー後、Docker 上の Nix で devShell と JDK 25 を確認し、ローカル PlantUML server を使って `mkdocs build --strict` も完走しました。高重要度の指摘はすべて修正済みで、Unit 1 を完了と判定します。

## 改善提案と対応

| 重要度 | 指摘 | 対応 |
| :--- | :--- | :--- |
| 高 | Cucumber の経路選択が production に漏れている | step definitions へ移し、production は domain 関数と HTTP handler のみに修正 |
| 高 | HTTP 経路が request を読まず偽陽性になる | `/score/{word}` の path を handler が解析するテストへ修正 |
| 高 | MkDocs strict 未確認なのに完了表示になっている | 計画と索引を「検証中」へ戻し、CI に strict build を追加 |
| 高 | Nix 内の Gradle 版が JDK 25 非対応になり得る | nixpkgs の Gradle を外し、Gradle Wrapper 9.7.0 を唯一の正とした |
| 中 | 全 subproject に全依存を配布している | Unit 1 module の `build.gradle.kts` へ依存を限定 |
| 中 | Cucumber のタグ別実行がない | `@domain` / `@http` を付け、system property を test task へ渡して実行確認 |
| 中 | effect を 1 回だけ呼ぶテスト名と検証が不一致 | counter で呼び出し回数 1 を検証 |
| 中 | 読者向け前提が不足 | JDK 25、Docker daemon、初回ネットワーク要件を README と記事へ追記 |

## 完了後の確認事項

- GitHub Actions の初回実行結果を確認する

GitHub Actions は固定 PostgreSQL service を使わず、Testcontainers とローカル PlantUML service を起動する定義です。ローカルでは同じコマンドが green になっています。
