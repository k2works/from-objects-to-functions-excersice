---
type: ADR
title: "ADR-001 サンプル実装に Kotlin 2.2 / JDK 21 を採用する"
description: "Zettai 連載 Kotlin 版のサンプル実装で、原著の Kotlin 1.8.20 / JDK 11 ではなく Kotlin 2.2（Gradle プラグイン）/ JDK 21 を採用し、ビルドの正を Gradle 側に置く決定と、devShell の kotlinc 2.3 との二重管理を許容する理由・影響・遵守確認方法を記録する。"
tags: [adr, kotlin, zettai]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-26T13:13:06Z }
---

# ADR-001 サンプル実装に Kotlin 2.2 / JDK 21 を採用する

Zettai 連載のサンプル実装が使う Kotlin と JDK のバージョンを決める。

日付: 2026-09-26

## ステータス

2026-09-26 提案されました

## コンテキスト

連載 [Zettai](../article/zettai/index.md) は Uberto Barbini 著『From Objects to Functions』を下敷きにしている。原著のコンパニオンコード（`references/fotf/`）は **Kotlin 1.8.20 / `jvmToolchain(11)`** でビルドされている。

一方、本連載は 2026 年に公開する読者に向けたものであり、読者が手元で再現することが Intent の中核にある。原著の 2023 年時点のツールチェーンをそのまま使うと、読者が現在の Kotlin で書いたときに動かないコードを学ぶことになる。

Unit 1 のスパイク（ステップ 1-1.0）で、リポジトリの nixpkgs（`nixos-unstable`）が提供するバージョンと Maven Central の最新安定版を調べた。

| 対象 | 調査結果 |
| :--- | :--- |
| nixpkgs `kotlin` | 2.3.0（CLI コンパイラ `kotlinc`） |
| nixpkgs `jdk21` | 21.0.8 |
| nixpkgs `gradle` | 8.14.3 |
| Maven Central `kotlin-gradle-plugin` の最新安定版 | 2.2.0（2.3.0 は未公開） |
| Maven Central `junit-bom` の最新安定版 | 5.12.2 |
| Maven Central `strikt-core` の最新版 | 0.35.1 |

ここで問題になるのは、**devShell が提供する CLI コンパイラ（2.3.0）と、Gradle がビルドに使う Kotlin コンパイラ（プラグイン 2.2.0）のバージョンが一致しない**ことである。nixpkgs は unstable を追うため、Maven Central の安定版より先行することがある。

## 決定

サンプル実装は **Kotlin 2.2（Gradle プラグイン）/ JDK 21** を採用する。原著の Kotlin 1.8.20 / JDK 11 は踏襲しない。

あわせて次を決める。

1. **ビルドの正は Gradle 側に置く。** コンパイラのバージョンは `apps/kotlin/zettai/gradle/libs.versions.toml` の `kotlin` で決まる。devShell の `kotlinc` は単発のスクリプトを試すための CLI であり、ビルドには関与しない
2. **devShell の `kotlin` のバージョンは固定しない。** nixpkgs の更新に追従させる。CLI とビルドのバージョン差は許容する
3. **JDK は `jvmToolchain(21)` で明示する。** ホストの JAVA_HOME に依存させない
4. **依存バージョンは `libs.versions.toml` に集中させる。** `build.gradle.kts` にリテラルのバージョンを書かない
5. **原著と挙動や API が異なる箇所は、該当する章の記事に注記する。** 読者が原著と行き来できるようにするため

### 検討した代替案

| 案 | 採らなかった理由 |
| :--- | :--- |
| 原著と同じ Kotlin 1.8.20 / JDK 11 を使う | 2026 年の読者が手元で再現したときに、現在の Kotlin と挙動が異なる。連載の価値が「今書けるコード」であることと矛盾する |
| devShell の `kotlin` を 2.2.0 にピン留めして CLI とビルドを揃える | nixpkgs に該当バージョンが無ければオーバーレイを書くことになり、環境定義が他の 13 環境と構造がずれる。CLI はビルドに関与しないので揃える利益が小さい |
| Gradle プラグインを 2.3.0 にして CLI と揃える | Maven Central に未公開で解決できない |

## 影響

- **読者は JDK 21 を前提にできる。** `nix develop .#kotlin` で JDK 21 が入るため、ホストの JDK バージョンに左右されない
- **原著のコードをそのまま貼っても動かない箇所が出る。** 特に第 5 章以降のコレクション API と、第 9 章の JDBC 周りで差が出る可能性がある。差が出たら該当章に注記する（決定 5）
- **`kotlinc -version` と `./gradlew` のビルドログでバージョンが食い違って見える。** 読者が混乱しうるため、`apps/kotlin/zettai/README.md` に「ビルドの正は Gradle 側」であることを明記した
- **nixpkgs の更新で devShell の Kotlin が上がっても、ビルドは壊れない。** 逆に言えば、CLI で試したコードがビルドで通らないことはありうる

## コンプライアンス

- `apps/kotlin/zettai/build.gradle.kts` に `jvmToolchain(21)` があること
- `apps/kotlin/zettai/gradle/libs.versions.toml` に `kotlin` のバージョンがあり、`build.gradle.kts` にリテラルのバージョンが無いこと
- `nix develop .#kotlin` で `javac -version` が 21 系を返すこと
- `./gradlew check` が green であること

上記は Unit 1 の Deployment Unit の完了条件に含まれている。

## 備考

- 決定者: k2works
- 起案: Unit 1 のスパイク（[Unit 1 の Bolt 計画](../development/iteration_plan-1.md) ステップ 1-1.0）
- 関連: [リリース計画](../development/release_plan.md) 技術リスク 1、[執筆計画](../article/outline.md) 実行環境の方針
