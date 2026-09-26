---
type: ADR
title: "ADR-003 http4k 6.x を採用し原著の 4.x との差分は章ごとに注記する"
description: "Zettai 連載 Kotlin 版のサンプル実装で、原著の http4k 4.48 ではなく 6.15 を採用する決定。スパイクで確認した API 互換性の範囲、差分が出た場合の注記方針、影響と遵守確認方法を記録する。"
tags: [adr, kotlin, zettai]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-26T13:50:12Z }
---

# ADR-003 http4k 6.x を採用し原著の 4.x との差分は章ごとに注記する

日付: 2026-09-26

## ステータス

2026-09-26 提案されました

## コンテキスト

原著『From Objects to Functions』のコンパニオンコードは **http4k 4.48.0.0** を使っています（`references/fotf/gradle.properties`）。本連載は [ADR-001](ADR-001-kotlin-toolchain.md) で「2026 年の読者が手元で書くコードに合わせる」と決めており、http4k も現在の版を使うのが筋です。

一方、メジャーバージョンが 2 つ上がっているため、原著の設計をそのままなぞれない可能性がありました。第 2 章の構成が変わるほどの差があれば、章立てから見直す必要があります。

Unit 2 のスパイク（[Bolt 計画](../development/iteration_plan-2.md) ステップ 2-1.0）で、http4k **6.15.1.0** の主要 API を確かめました。

| 確認項目 | 結果 |
| :--- | :--- |
| `routes(...)` と `bind` によるルーティング | 4.x と同じ記法で動作 |
| `Request` / `Response` / `Status` | 変更なし |
| `HttpHandler`（`(Request) -> Response`） | 変更なし。**第 2 章の主題「矢印で設計する」がそのまま成立する** |
| `Request.path("...")` によるパス変数の取得 | 変更なし |
| `asServer(Jetty(port))` によるサーバー起動 | 変更なし |
| Kotlin 2.2 / JUnit 5.12 との組み合わせ | 問題なし |

第 2 章で使う範囲では **API の差分が見つかりませんでした**。

## 決定

サンプル実装は **http4k 6.15.1.0** を採用します。原著の 4.48.0.0 は踏襲しません。

1. バージョンは `apps/kotlin/zettai/gradle/libs.versions.toml` の `http4k` で管理する
2. 第 2 章の時点では原著との差分が無いため、章に注記を書かない。**差分が無いことを確かめた事実は本 ADR に残す**
3. 後続の章（とくに第 11 章のテンプレートエンジン、第 12 章の JSON 周り）で差分が出たら、その章に注記する（[ADR-001](ADR-001-kotlin-toolchain.md) の決定 5）
4. サーバー実装は Jetty を使う（原著と同じ）

## 影響

- 読者は現在の http4k のドキュメントをそのまま参照できる
- 第 2 章の「矢印で設計する」という主題が、バージョンを上げても成立する。`HttpHandler` が `(Request) -> Response` の型エイリアスであることは 6.x でも変わらない
- 後続の章で差分が出る可能性は残る。各章のスパイクで確認する

## コンプライアンス

- `libs.versions.toml` に `http4k` のバージョンがあり、`build.gradle.kts` にリテラルのバージョンが無いこと
- `./gradlew check` が green であること

## 備考

- 起案: Unit 2 のスパイク（ステップ 2-1.0）
- 関連: [ADR-001](ADR-001-kotlin-toolchain.md)、[Unit 2 の Bolt 計画](../development/iteration_plan-2.md)
